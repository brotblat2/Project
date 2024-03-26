package edu.yu.cs.com1320.project.stage4.impl;



import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.stage4.Document;

import java.net.URI;
import java.util.*;

public class DocumentImpl implements Document {
    private URI uri;
    private String txt;
    private byte[] binaryData;
    private HashTableImpl<String, String> meta;
    private HashMap<String, Integer> wordCounts;



    public DocumentImpl(URI uri, String txt){
        if (uri==null || uri.toString().isEmpty() || txt==null || txt.isEmpty()){
            throw new IllegalArgumentException("Empty or null URI or value");
        }
        this.uri=uri;
        this.txt=txt;
        this.meta= new HashTableImpl<>();
        this.wordCounts=this.wordCountTable();
    }

    public DocumentImpl(URI uri, byte[] binaryData){
        if (uri == null) {
            throw new IllegalArgumentException("Empty or null URI or value");
        } else if (uri.toString().isEmpty()) {
            throw new IllegalArgumentException("Empty or null URI or value");
        } else if (binaryData == null) {
            throw new IllegalArgumentException("Empty or null URI or value");
        } else if (binaryData.length == 0) {
            throw new IllegalArgumentException("Empty or null URI or value");
        }

        this.uri=uri;
        this.binaryData=binaryData;
        this.meta= new HashTableImpl<>();


    }

    /**
     * @param key   key of document metadata to store a value for
     * @param value value to store
     * @return old value, or null if there was no old value
     * @throws IllegalArgumentException if the key is null or blank
     */

    @Override
    public String setMetadataValue(String key, String value){
        if (key==null || key.isEmpty() ){
            throw new IllegalArgumentException("Empty metadata value");
        }
        String old=this.meta.get(key);
        this.meta.put(key, value);
        return old;
    }

    /**
     * @param key metadata key whose value we want to retrieve
     * @return corresponding value, or null if there is no such key
     * @throws IllegalArgumentException if the key is null or blank
     */
    public String getMetadataValue(String key){
        if (key==null || key.isEmpty() ){
            throw new IllegalArgumentException("Empty metadata key");
        }
        return this.meta.get(key);
    }

    /**
     * @return a COPY of the metadata saved in this document
     */
    public HashTableImpl<String, String> getMetadata(){
        HashTableImpl<String, String> copy = new HashTableImpl<>();
        Set<String> keys= meta.keySet();
        Collection<String> values= meta.values();
        for (String key :keys){
            copy.put(key, meta.get(key));
        }
        return copy;

    }

    /**
     * @return content of text document
     */
    //Do I throw an error or just return null?
    public String getDocumentTxt(){
        return this.txt;
    }

    /**
     * @return content of binary data document
     */
    public byte[] getDocumentBinaryData(){
        return this.binaryData;
    }

    /**
     * @return URI which uniquely identifies this document
     */
    public URI getKey(){
        return this.uri;

    }
    private HashSet <Integer> numberSet(){
       HashSet<Integer> set= new HashSet<>();
        for (int i=0;i<128;i++){
            if (i<48|| (i>57&&i<65) || (i>90 &&i<97) || i>123){
                set.add(i);
            }
        }
        set.remove(32);//spacebar
        return set;
    }
    //strips out non-alpha chars and splits based on spaces. Iterate thru array and add to hashtable
    private HashMap <String, Integer> wordCountTable(){
        String fixed= txt;
        HashSet<Integer> set= numberSet();
        for ( int i: set) {
            char ch = (char) i;
            String s = "" + ch;
            fixed = txt.replace( s, "");
        }
        String[] words= fixed.split(" ");
        HashMap<String, Integer> hash= new HashMap<>();
        for (String s: words){
            Integer val=hash.get(s);
            //hash.merge(s, 1, Integer::sum);
            if (val==null){
                hash.put(s, 1);
            }
            else{
                hash.put(s, val+1);
            }
        }
        return hash;
    }
    /**
     * how many times does the given word appear in the document?
     * @param word
     * @return the number of times the given words appears in the document. If it's a binary document, return 0.
     */
    @Override
    public int wordCount(String word) {
        if (this.txt==null)
            return 0;
        Integer i= this.wordCounts.get(word);
        if (i==null) return 0;
        return i;
    }

    /**
     * @return all the words that appear in the document
     */
    @Override
    public Set<String> getWords() {
        return wordCounts.keySet();
    }

    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + (txt != null ? txt.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(binaryData);
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

}