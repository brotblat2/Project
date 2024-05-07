
package edu.yu.cs.com1320.project.stage6.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

import edu.yu.cs.com1320.project.impl.*;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.DocumentStore;
import edu.yu.cs.com1320.project.undo.CommandSet;
import edu.yu.cs.com1320.project.undo.GenericCommand;
import edu.yu.cs.com1320.project.undo.Undoable;

import javax.print.Doc;

public class DocumentStoreImpl implements DocumentStore {

    private BTreeImpl<URI, DocumentImpl> store;
    private StackImpl<Undoable> commandStack;
    private TrieImpl<DocSub> documentTrie;
    private MinHeapImpl<DocSub> documentMinHeap;
    private int docCount;
    private int byteCount;
    private int maxDocumentCount;
    private int maxDocumentBytes;


    public DocumentStoreImpl(){
        this.commandStack= new StackImpl<>();
        this.store= new BTreeImpl<>();
        this.documentTrie=new TrieImpl<>();
        this.documentMinHeap= new MinHeapImpl<>();
    }

    private class DocSub implements Comparable<DocSub> {
        URI uri;
        private DocSub(DocumentImpl doc){
            this.uri=doc.getKey();
        }
        private DocSub(URI url){
            this.uri=url;
        }
        private DocumentImpl getDoc(){
            return store.get(this.uri);
        }
        private long getLastUseTime(){
            return store.get(uri).getLastUseTime();
        }
        private URI getKey(){
            return this.uri;
        }

        @Override
        public int hashCode() {
            int result = uri.hashCode();
            result = 31 * result + (store.get(uri).getDocumentTxt() != null ? store.get(uri).getDocumentTxt().hashCode() : 0);
            result = 31 * result + Arrays.hashCode(store.get(uri).getDocumentBinaryData());
            return Math.abs(result);
        }

        @Override
        public boolean equals(Object o){
            if (this.hashCode()==o.hashCode()){
                return true;
            }
            else{
                return false;
            }
        }

        @Override
        public int compareTo(DocSub o) {
            long dif = this.getLastUseTime() - o.getLastUseTime();
            if (dif < 0) return -1;
            else if (dif > 0) return 1;
            else return 0;
        }
    }

    /**
     * the two document formats supported by this document store.
     * Note that TXT means plain text, i.e. a String.
     */

    /**
     * set the given key-value metadata pair for the document at the given uri
     *
     * @param uri
     * @param key
     * @param value
     * @return the old value, or null if there was no previous value
     * @throws IllegalArgumentException if the uri is null or blank, if there is no document stored at that uri, or if the key is null or blank
     */
    public String setMetadata(URI uri, String key, String value){
        if (uri==null || uri.toString().isEmpty()){
            throw new IllegalArgumentException("Empty uri");
        }
        if (this.store.get(uri)==null){
            throw new IllegalArgumentException("No Doc");
        }
        if (key==null || key.isEmpty()){
            throw new IllegalArgumentException("Inputted key is empty");
        }
        String data= this.store.get(uri).setMetadataValue(key, value);
        this.store.get(uri).setLastUseTime(System.nanoTime());
        documentMinHeap.reHeapify(new DocSub(uri));

        Consumer<URI> u = (squash) -> {
            this.store.get(uri).setMetadataValue(key, data);
            this.store.get(uri).setLastUseTime(System.nanoTime());
            documentMinHeap.reHeapify(new DocSub(uri));
        };

        GenericCommand<URI> com=new GenericCommand<URI>(uri, u);
        commandStack.push(com);
        return data;
    }

    /**
     * get the value corresponding to the given metadata key for the document at the given uri
     *
     * @param uri
     * @param key
     * @return the value, or null if there was no value
     * @throws IllegalArgumentException if the uri is null or blank, if there is no document stored at that uri, or if the key is null or blank
     */
    public String getMetadata(URI uri, String key){
        if (uri==null || uri.toString().isEmpty()){
            throw new IllegalArgumentException("Empty uri");
        }
        if (this.store.get(uri)==null){
            throw new IllegalArgumentException("No Doc");
        }

        if (key==null || key.isEmpty()){
            throw new IllegalArgumentException("Inputted key is empty");
        }
        this.store.get(uri).setLastUseTime(System.nanoTime());
        documentMinHeap.reHeapify(new DocSub(uri));
        return this.store.get(uri).getMetadataValue(key);

    }

    /**
     * @param input  the document being put
     * @param uri    unique identifier for the document
     * @param format indicates which type of document format is being passed
     * @return if there is no previous doc at the given URI, return 0. If there is a previous doc, return the hashCode of the previous doc. If InputStream is null
     * ,this is a delete, and thus return either the hashCode of the deleted doc or 0 if there is no doc to delete.
     * @throws IOException              if there is an issue reading input
     * @throws IllegalArgumentException if uri is null or empty, or format is null
     */
    public int put(InputStream input, URI uri, DocumentFormat format) throws IOException{
        if (uri==null || uri.toString().isEmpty()){
            throw new IllegalArgumentException("Empty uri");
        }
        if (format==null){
            throw new IllegalArgumentException("Null Format");
        }
        if (this.maxDocumentBytes>0 && input.readAllBytes().length>this.maxDocumentBytes) throw new IllegalArgumentException();

        //Return Value and acting as delete
        int x;
        if (input==null){
            x=store.get(uri).hashCode();
            if (this.delete(uri)) return x;
            else return 0;
        }
        // return value
        if (store.get(uri)==null){
            x=0;
        }
        else{
            x= store.get(uri).hashCode();
        }

        putImple(input, uri, format);

        makeSpace();

        return x;
    }
    private void putImple(InputStream input, URI uri, DocumentFormat format) throws IOException {
        if (format==DocumentFormat.BINARY){
            binaryPut(input, uri);
        }
        else {
            byte[] b = input.readAllBytes();
            String s = new String(b);
            DocumentImpl d = new DocumentImpl(uri, s);
            byteCount+=b.length;
            docCount++;

            DocumentImpl oldDoc =this.store.get(uri);

            if (oldDoc!=null) {
                DocSub ods= new DocSub(oldDoc);
                for (String word : oldDoc.getWords()) {
                    documentTrie.delete(word,ods);
                }
                deleteFromHeap(oldDoc);
            }

            this.store.put(uri, d);
            //put time in to TRIE
            Set<String> wordSet = d.getWords();
            DocSub ds= new DocSub(d);


            for (String word : wordSet) {
                documentTrie.put(word, ds);
            }

            this.store.get(uri).setLastUseTime(System.nanoTime());
            documentMinHeap.insert(ds);
            documentMinHeap.reHeapify(ds);



            Consumer<URI> u = (squash) -> {
                Document newDoc=this.store.get(uri);
                //this first if is else if is just making sure that we are under the cap
                if ((oldDoc!=null) && oldDoc.getDocumentBinaryData()!=null && oldDoc.getDocumentBinaryData().length>this.maxDocumentBytes && this.maxDocumentBytes>0);
                else if ( (oldDoc!=null) && oldDoc.getDocumentTxt()!=null && oldDoc.getDocumentTxt().getBytes().length>this.maxDocumentBytes && this.maxDocumentBytes>0);

                else {

                    deleteFromHeap(newDoc);

                    for (String word : newDoc.getWords()) {
                        documentTrie.delete(word, ds);
                    }
                    this.byteCount-=b.length;
                    if (oldDoc==null) this.docCount--;

                    this.store.put(uri, oldDoc);

                    DocSub ods= new DocSub(oldDoc);

                    if (this.store.get(uri) != null) {
                        this.store.get(uri).setLastUseTime(System.nanoTime());
                        documentMinHeap.insert(ods);
                        this.byteCount+=this.getDocBytes(ods.getDoc());
                        for (String word : ods.getDoc().getWords()) {
                            documentTrie.put(word, ods);
                        }
                    }

                }


            };
            GenericCommand<URI> com = new GenericCommand<>(uri, u);
            commandStack.push(com);
        }
    }

    private void binaryPut(InputStream input, URI uri) throws IOException {
        byte[] b = input.readAllBytes();
        DocumentImpl newDoc= new DocumentImpl(uri, b);

        DocumentImpl oldDoc=this.store.get(uri);
        //don't think I need to touch the heap honestly, but at least I reheapify
        deleteFromHeap(oldDoc);

        this.store.put(uri, newDoc);
        documentMinHeap.insert(new DocSub(newDoc));

        this.store.get(uri).setLastUseTime(System.nanoTime());
        byteCount+=b.length;
        docCount++;

        Consumer <URI> u = (squash) -> {
            if (oldDoc!=null && oldDoc.getDocumentBinaryData()!=null && oldDoc.getDocumentBinaryData().length>this.maxDocumentBytes && this.maxDocumentBytes>0);
            else if (oldDoc!=null && oldDoc.getDocumentTxt()!=null && oldDoc.getDocumentTxt().getBytes().length>this.maxDocumentBytes && this.maxDocumentBytes>0);
            else {
                deleteFromHeap(newDoc);
                this.store.put(uri, oldDoc);
                this.byteCount-=this.getDocBytes(newDoc);
                if (this.store.get(uri) != null) {
                    this.store.get(uri).setLastUseTime(System.nanoTime());
                    documentMinHeap.insert(new DocSub(uri));
                    this.byteCount+=this.getDocBytes(oldDoc);
                }
                else{
                    this.docCount--;
                }
            }
        };
        GenericCommand<URI> com=new GenericCommand<>(uri, u);
        commandStack.push(com);
    }


    /**
     * @param url the unique identifier of the document to get
     * @return the given document
     */
    public Document get(URI url){
        if (this.store.get(url)!=null) {
            this.store.get(url).setLastUseTime(System.nanoTime());
            documentMinHeap.reHeapify(new DocSub(url));
        }
        return this.store.get(url);
    }

    /**
     * @param url the unique identifier of the document to delete
     * @return true if the document is deleted, false if no document exists with that URI
     */
    public boolean delete(URI url) {
        if (this.store.get(url) == null) {
            return false;
        }
        //NEED TO DELETE FROM THE TRIE
        DocumentImpl doc = this.store.get(url);
        doc.setLastUseTime(System.nanoTime());
        deleteFromHeap(doc);

        Set<String> words = doc.getWords();
        for (String word : words) {
            documentTrie.delete(word, new DocSub(doc));
        }

        docCount--;
        byteCount-=this.getDocBytes(doc);

        this.store.put(url, null);


        GenericCommand<URI> com = undoDeleteCommand(url, doc);
        commandStack.push(com);

        return true;
    }

    private GenericCommand<URI> undoDeleteCommand(URI url, DocumentImpl doc) {
        Consumer<URI> u = (squash) -> {
            if (doc.getDocumentBinaryData()!=null && doc.getDocumentBinaryData().length>this.maxDocumentBytes && this.maxDocumentBytes>0);
            else if (doc.getDocumentTxt()!=null && doc.getDocumentTxt().getBytes().length>this.maxDocumentBytes && this.maxDocumentBytes>0);
            else {
                this.store.put(url, doc);
                DocSub docSub=new DocSub(doc);
                this.store.get(url).setLastUseTime(System.nanoTime());
                documentMinHeap.insert(docSub);
                for (String word : doc.getWords()) {
                    this.documentTrie.put(word, docSub);
                }
                docCount++;
                byteCount+=this.getDocBytes(doc);
                makeSpace();
            }
        };

        GenericCommand<URI> com = new GenericCommand<>(url, u);
        return com;
    }

    private Document privateDeleteFromTable(URI url){
        if (this.store.get(url)==null){
            return null;
        }
        DocumentImpl doc= this.store.put(url, null);
        //NEED TO DELETE FROM THE TRIE
        return doc;
    }

    @Override
    public void undo() throws IllegalStateException {
        //fixed from old assignment
        if (commandStack.size()==0) {throw new IllegalStateException();}
        while (commandStack.peek()==null){
            if (commandStack.size()==0){throw new IllegalStateException();}
            commandStack.pop();
        }
        Undoable c= commandStack.pop();
        c.undo();
    }

    private boolean hasURI(Undoable undoable, URI uri){
        if (undoable instanceof GenericCommand){
            return ((GenericCommand<URI>) undoable).getTarget().toString().equals(uri.toString());
        }
        else{
            return (((CommandSet<URI>)undoable).containsTarget(uri));
        }
    }

    @Override
    public void undo(URI url) throws IllegalStateException {
        StackImpl<Undoable> temp=new StackImpl<>();
        boolean found=false;
        while (commandStack.size()!=0){
            if (commandStack.peek()==null){
                commandStack.pop();
            }
            else if (!(hasURI(commandStack.peek(), url))){
                temp.push(commandStack.pop());
            }
            else{
                Undoable u=commandStack.peek();
                if (u instanceof CommandSet<?>){
                    ((CommandSet)u).undo(url);
                    if(((CommandSet)u).size()==0) commandStack.pop();
                }
                else {
                    u.undo();
                    commandStack.pop();
                }
                found=true;
                break;
            }
        }
        if (!found){
            throw new IllegalStateException();
        }

        while (temp.peek()!=null){
            commandStack.push(temp.pop());
        }

    }


    /**
     * Retrieve all documents whose text contains the given keyword.
     * Documents are returned in sorted, descending order, sorted by the number of times the keyword appears in the document.
     * Search is CASE SENSITIVE.
     * @param keyword
     * @return a List of the matches. If there are no matches, return an empty list.
     */


    @Override
    public List<Document> search(String keyword) {
       //make a comparator
        List<Document> lis=  documentTrie.getSorted(keyword, new DocumentComparator(keyword));

        if (lis==null || lis.isEmpty()) return new ArrayList<>();

        for (Document d:lis){
            d.setLastUseTime(System.nanoTime());
            documentMinHeap.reHeapify(d);
        }
        return lis;
    }
    /**
     * Retrieve all documents that contain text which starts with the given prefix
     * Documents are returned in sorted, descending order, sorted by the number of times the prefix appears in the document.
     * Search is CASE SENSITIVE.
     * @param keywordPrefix
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> searchByPrefix(String keywordPrefix) {
        List<Document> lis=  documentTrie.getAllWithPrefixSorted(keywordPrefix, new DocumentComparatorPrefix(keywordPrefix));
        for (Document d:lis){
            d.setLastUseTime(System.nanoTime());
            documentMinHeap.reHeapify(d);

        }
        return lis;


    }

    /**
     * Completely remove any trace of any document which contains the given keyword
     * Search is CASE SENSITIVE.
     * @param keyword
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAll(String keyword) {
        Set<Document> oldDocs= new HashSet<>( documentTrie.get(keyword));
        Set<URI> oldUris= new HashSet<>();
        for(Document d:oldDocs) {
            d.setLastUseTime(System.nanoTime());
            documentMinHeap.reHeapify(d);
            oldUris.add(d.getKey());
        }

        if (oldDocs==null || oldDocs.isEmpty()) return new HashSet<>();

        CommandSet<URI> cset= new CommandSet<>();

        for(URI uri:oldUris){
            cset.addCommand(undoDeleteCommand(uri, this.store.get(uri)));
        }

        for(Document d:oldDocs){
            for (String s:d.getWords()){
                documentTrie.delete(s,d);
            }
            deleteFromHeap(d);
            byteCount-=getDocBytes((DocumentImpl) d);
            docCount--;
        }

        for(URI u:oldUris){
            this.privateDeleteFromTable(u);
        }

        commandStack.push(cset);
        return oldUris;
    }
    /**
     * Completely remove any trace of any document which contains a word that has the given prefix
     * Search is CASE SENSITIVE.
     * @param keywordPrefix
     * @return a Set of URIs of the documents that were deleted.
     */
    //UP TO HERE!!!!!!!!!
    @Override
    public Set<URI> deleteAllWithPrefix(String keywordPrefix) {
        List<Document> oldDocs= documentTrie.getAllWithPrefixSorted(keywordPrefix, new DocumentComparatorPrefix(keywordPrefix));
        Set<URI> oldUris= new HashSet<>();
        for(Document d:oldDocs) {
            oldUris.add(d.getKey());
        }

        if (oldDocs==null || oldDocs.isEmpty()) return new HashSet<>();

        CommandSet<URI> cset= new CommandSet<>();

        for(URI uri:oldUris){
            cset.addCommand(undoDeleteCommand(uri, this.store.get(uri)));
        }

        for(Document d:oldDocs){
            for (String s:d.getWords()){
                documentTrie.delete(s,d);
            }
            deleteFromHeap(d);
            byteCount-=getDocBytes((DocumentImpl) d);
            docCount--;
        }

        for(URI u:oldUris){
            this.privateDeleteFromTable(u);
        }

        commandStack.push(cset);
        return oldUris;
    }

    /**
     * @param keysValues metadata key-value pairs to search for
     * @return a List of all documents whose metadata contains ALL OF the given values for the given keys. If no documents contain all the given key-value pairs, return an empty list.
     */

    @Override
    public List<Document> searchByMetadata(Map<String, String> keysValues) {
        List<Document> lis=privateSearchByMetadata(keysValues);
        for (Document d:lis) {
            d.setLastUseTime(System.nanoTime());
            documentMinHeap.reHeapify(d);
        }
        return lis;
    }


    private List<Document> privateSearchByMetadata(Map<String, String> keysValues) {
        List<Document> docsList=documentTrie.getAllWithPrefixSorted("", new DocumentComparatorPrefix(""));
        Set<Document> docSet=new HashSet<>();
        for(Document d:docsList){
            boolean has=true;
            for (String s: keysValues.keySet()){
                if (!(d.getMetadata().containsKey(s) && keysValues.get(s).equals(d.getMetadataValue(s)))){
                    has=false;
                    break;
                }
            }
            if (has){
                docSet.add(d);
            }

        }

        List<Document> returnList=new ArrayList<>(docSet);
        return returnList;
    }

    /**
     * Retrieve all documents whose text contains the given keyword AND which has the given key-value pairs in its metadata
     * Documents are returned in sorted, descending order, sorted by the number of times the keyword appears in the document.
     * Search is CASE SENSITIVE.
     * @param keyword
     * @param keysValues
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> searchByKeywordAndMetadata(String keyword, Map<String, String> keysValues) {
        List<Document> docsList=privateSearchByMetadata(keysValues);
        if(docsList==null || docsList.isEmpty()) return new ArrayList<>();
        List<Document> list=search(keyword);

        if(list==null || list.isEmpty()) return new ArrayList<>();

        for (Document doc : new ArrayList<>(docsList)) {
            if (!list.contains(doc)) {
                docsList.remove(doc);
            }
        }
        for (Document d:docsList) {
            d.setLastUseTime(System.nanoTime());
            documentMinHeap.reHeapify(d);
        }
        return docsList;
    }
    /**
     * Retrieve all documents that contain text which starts with the given prefix AND which has the given key-value pairs in its metadata
     * Documents are returned in sorted, descending order, sorted by the number of times the prefix appears in the document.
     * Search is CASE SENSITIVE.
     * @param keywordPrefix
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> searchByPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) {
        List<Document> docsList=privateSearchByMetadata(keysValues);
        List<Document> list=searchByPrefix(keywordPrefix);

        for (Document doc : new ArrayList<>(docsList)) {
            if (!list.contains(doc)) {
                docsList.remove(doc);
            }
        }
        for (Document d:docsList) {
            d.setLastUseTime(System.nanoTime());
            documentMinHeap.reHeapify(d);
        }
        return docsList;
    }
    /**
     * Completely remove any trace of any document which has the given key-value pairs in its metadata
     * Search is CASE SENSITIVE.
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithMetadata(Map<String, String> keysValues) {
        List<Document> oldDocs= searchByMetadata(keysValues);
        Set<URI> oldUris= new HashSet<>();
        for(Document d:oldDocs) {
            oldUris.add(d.getKey());
        }

        if (oldDocs==null || oldDocs.isEmpty()) return new HashSet<>();

        CommandSet<URI> cset= new CommandSet<>();

        for(URI uri:oldUris){
            cset.addCommand(undoDeleteCommand(uri, this.store.get(uri)));
        }

        for(Document d:oldDocs){
            for (String s:d.getWords()){
                documentTrie.delete(s,d);
            }
            deleteFromHeap(d);
            byteCount-=getDocBytes((DocumentImpl) d);
            docCount--;
        }

        for(URI u:oldUris){
            this.privateDeleteFromTable(u);
        }

        commandStack.push(cset);
        return oldUris;
    }
    /**
     * Completely remove any trace of any document which contains the given keyword AND which has the given key-value pairs in its metadata
     * Search is CASE SENSITIVE.
     * @param keyword
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithKeywordAndMetadata(String keyword, Map<String, String> keysValues) {
        List<Document> oldDocs= searchByKeywordAndMetadata(keyword,keysValues);
        Set<URI> oldUris= new HashSet<>();
        for(Document d:oldDocs) {
            oldUris.add(d.getKey());
        }

        if (oldDocs==null || oldDocs.isEmpty()) return new HashSet<>();

        CommandSet<URI> cset= new CommandSet<>();

        for(URI uri:oldUris){
            cset.addCommand(undoDeleteCommand(uri, this.store.get(uri)));
        }

        for(Document d:oldDocs){
            for (String s:d.getWords()){
                documentTrie.delete(s,d);
            }
            deleteFromHeap(d);
            byteCount-=getDocBytes((DocumentImpl) d);
            docCount--;
        }

        for(URI u:oldUris){
            this.privateDeleteFromTable(u);
        }

        commandStack.push(cset);
        return oldUris;
    }
    /**
     * Completely remove any trace of any document which contains a word that has the given prefix AND which has the given key-value pairs in its metadata
     * Search is CASE SENSITIVE.
     * @param keywordPrefix
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) {
        List<Document> oldDocs= searchByPrefixAndMetadata(keywordPrefix,keysValues);
        Set<URI> oldUris= new HashSet<>();
        for(Document d:oldDocs) {
            oldUris.add(d.getKey());
        }

        if (oldDocs==null || oldDocs.isEmpty()) return new HashSet<>();

        CommandSet<URI> cset= new CommandSet<>();

        for(URI uri:oldUris){
            cset.addCommand(undoDeleteCommand(uri, this.store.get(uri)));
        }

        for(Document d:oldDocs){
            for (String s:d.getWords()){
                documentTrie.delete(s,d);
            }
            deleteFromHeap(d);
            byteCount-=getDocBytes((DocumentImpl) d);
            docCount--;
        }

        for(URI u:oldUris){
            this.privateDeleteFromTable(u);
        }

        commandStack.push(cset);
        return oldUris;
    }

    @Override
    public void setMaxDocumentCount(int limit) {
        if(limit<1) throw new IllegalArgumentException();
        this.maxDocumentCount=limit;

        if (this.docCount>limit){

            List<Document> docList= new ArrayList<>();
            for (int i=0; i<this.docCount-limit; i++){
                docList.add(documentMinHeap.remove());
            }

            List<URI> uriList= removeFromStackPastLimit(docList);


            for (URI url:uriList){
                if (this.store.get(url)!=null) this.store.put(url,null);
            }

            for (Document d:docList) {
                if (d.getWords() != null) {
                    for (String word : d.getWords()) {
                        if (documentTrie.get(word).contains(d)) documentTrie.delete(word, d);
                    }
                }
            }
        }
    }


    private List<URI> removeFromStackPastLimit(List<Document> docList) {
        List<URI> uriList=new ArrayList<>();

        for (Document doc : docList){
            uriList.add(doc.getKey());
        }

        StackImpl<Undoable> temp= new StackImpl<>();

        while(commandStack.size()!=0){

            if (commandStack.peek() instanceof GenericCommand<?>) {

                GenericCommand<URI> peek = (GenericCommand<URI>) commandStack.pop();

                if (!uriList.contains(peek.getTarget())) {
                    temp.push(peek);
                }
            }

            else{
                CommandSet<URI> peek = (CommandSet<URI>) commandStack.pop();
                CommandSet<URI> rep=new CommandSet<>();
                if (peek!=null) for(GenericCommand<URI> gc:peek){
                    if (!(uriList.contains(gc.getTarget()))){
                        rep.addCommand(gc);
                    }
                }
                if (rep.size()!=0) temp.push(rep);
            }

        }
        while (temp.size()!=0){
            this.commandStack.push(temp.pop());
        }
        return uriList;
    }

    @Override
    public void setMaxDocumentBytes(int limit) {
        if(limit<1) throw new IllegalArgumentException();
        this.maxDocumentBytes=limit;
        int b=this.byteCount;
        List<Document> docList=new ArrayList<>();
        while (b>limit) {
            Document d = documentMinHeap.remove();
            if (d.getDocumentBinaryData() != null) b -= d.getDocumentBinaryData().length;
            if (d.getDocumentTxt() != null) b -= d.getDocumentTxt().getBytes().length;
            docList.add(d);
        }

        removeFromStackPastLimit(docList);

        for(Document d: docList) {

            if (d.getWords() != null) {
                for (String word : d.getWords()) {
                    documentTrie.delete(word, d);
                }
            }

            if (this.store.get(d.getKey()) != null) this.store.put(d.getKey(), null);
        }

    }
    private void makeSpace(){
        if (this.maxDocumentCount>0) setMaxDocumentCount(this.maxDocumentCount);
        if (this.maxDocumentBytes>0) setMaxDocumentBytes(this.maxDocumentBytes);
    }

    private void deleteFromHeap(Document doc){
        if (doc==null) return;
        MinHeapImpl<Document> temp=new MinHeapImpl<>();
        while (this.documentMinHeap.peek()!=null){
            Document d=this.documentMinHeap.remove();
            if (!d.equals(doc)){
                temp.insert(d);
            }
        }
        this.documentMinHeap=temp;
    }


    private int getDocBytes(DocumentImpl doc) {
        if (doc==null) return 0;
        if (doc.getWords().isEmpty()){
            return doc.getDocumentBinaryData().length;
        }
        else{
            return doc.getDocumentTxt().getBytes().length;
        }
    }


    private class DocumentComparator implements Comparator<Document> {
        private String word;
        private DocumentComparator(String word){
            this.word=word;
        }
        @Override
        public int compare(Document o1, Document o2) {
            return Integer.compare( o1.wordCount(word),o2.wordCount(word))*-1;
        }
    }
    private class DocumentComparatorPrefix implements Comparator<Document> {
        private String pre;
        private DocumentComparatorPrefix(String word){
            this.pre=word;
        }
        @Override
        public int compare(Document o1, Document o2) {
            int a=0;
            int b=0;
            for (String s: o1.getWords()){
                if(s.length()>=pre.length() && s.substring(0, pre.length()).equals(pre)){
                    a+= o1.wordCount(s);
                }
            }
            for (String s: o2.getWords()){
                if(s.length()>=pre.length() && s.substring(0, pre.length()).equals(pre)){
                    b+= o2.wordCount(s);
                }
            }

            return Integer.compare(a,b)*-1;
        }
    }
}