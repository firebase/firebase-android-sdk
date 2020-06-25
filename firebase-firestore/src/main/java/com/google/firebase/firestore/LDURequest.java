import java.lang.String;
import java.util.Map;

/**
* Represents a request in the LazyDatabaseUploader queue. It consists of the
* name of collection the user wants to change, a document name, and the requestInfo
* of their request. The requestInfo of their request is what usually goes inside
* the parentheses of the db.collection(col).document(doc).set(requestInfo) method
* It is a map with key type String and value type Object and it's the most important
* part of the request (since it's what you want to write to the database).
*/
public class LDURequest {
    private String collectionName;
    private String documentName;
    private Map<String, Object> requestInfo;
   
    public LDURequest(String colName, String docName, Map<String, Object> ct)
    {
        collectionName = colName;
        documentName = docName;
        requestInfo = ct;
    }
   
    public String getColName() { return collectionName; }
    public String getDocName() { return documentName; }
    public Map<String,Object> getContent() { return requestInfo; }
}