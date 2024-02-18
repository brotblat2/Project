package edu.yu.cs.com1320.project.stage1.impl;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentImplTest {

    @Test
    void ConstructorCheckError() {
        URI uri = URI.create("http://example.com");
        String s=null;
            assertThrows(IllegalArgumentException.class,()->{
                DocumentImpl doc= new DocumentImpl(uri, s);
            });
        }
    @Test
    void ConstructorCheckError1() {
        URI uri = URI.create("");
        String s="hello";
        assertThrows(IllegalArgumentException.class,()->{
            DocumentImpl doc= new DocumentImpl(uri, s);
        });
    }
    @Test
    void ConstructorCheckTXT() {
        URI uri = URI.create("http://example.com");
        String s= "hello";
        DocumentImpl doc= new DocumentImpl(uri, s);
        assertEquals(s, doc.getDocumentTxt());
    }
    @Test
    void ConstructorCheckBYTE() {
        URI uri = URI.create("http://example.com");
        byte[] b = {41, 42, 43, 44, 45};
        DocumentImpl doc= new DocumentImpl(uri, b);
        assertEquals(b, doc.getDocumentBinaryData());    }

    @Test
    void setMetadataValueNull() {
        URI uri = URI.create("http://example.com");
        String s= "hello";
        DocumentImpl doc= new DocumentImpl(uri, s);
        String p=null;
        assertThrows(IllegalArgumentException.class,()->{
            doc.setMetadataValue(p , "okey dokey");
        });
    }

    @Test
    void setMetadataValueEmpty() {
        URI uri = URI.create("http://example.com");
        String s= "hello";
        DocumentImpl doc= new DocumentImpl(uri, s);
        String p="";
        assertThrows(IllegalArgumentException.class,()->{
            doc.setMetadataValue(p , "okey dokey");
        });
    }

    @Test
    void setMetadataValueWorks() {
        URI uri = URI.create("http://example.com");
        String s= "hello";
        DocumentImpl doc= new DocumentImpl(uri, s);
        String p="key";
        doc.setMetadataValue(p , "okey dokey");
        assertEquals(doc.getMetadataValue(p), "okey dokey");
    }

    @Test
    void setMetadataValueReturnsStrings() {
        URI uri = URI.create("http://example.com");
        String s= "hello";
        DocumentImpl doc= new DocumentImpl(uri, s);
        String p="key";
        doc.setMetadataValue(p , "okey dokey");
        String old= doc.setMetadataValue(p, "new and improved");
        assertEquals(old, "okey dokey");
    }


    @Test
    void getMetadataCheck() {
        URI uri = URI.create("http://example.com");
        String s= "hello";
        DocumentImpl doc= new DocumentImpl(uri, s);
        doc.setMetadataValue("key" , "okey dokey");
        doc.setMetadataValue("key1", "okey dokey");
        HashMap <String, String> copy= new HashMap<>();
        copy.put("key", "okey dokey");
        copy.put("key1", "okey dokey");
        HashMap <String, String> hmap= doc.getMetadata();
        assertEquals(hmap, copy);
    }

    @Test
    void getKeyTest() {
        URI uri = URI.create("http://example.com");
        String s= "hello";
        DocumentImpl doc= new DocumentImpl(uri, s);
        assertEquals(doc.getKey(), uri);
    }

    @Test
    void testEqualsCheck() {
        URI uri = URI.create("http://example.com");
        String s= "hello";
        DocumentImpl doc= new DocumentImpl(uri, s);
        URI uri1 = URI.create("http://example.com");
        String s1= "hello";
        DocumentImpl doc1= new DocumentImpl(uri1, s1);
        assertTrue(doc1.equals(doc));
    }

}
