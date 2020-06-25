import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Timer;
import java.util.TimerTask;
import java.lang.String;
import java.util.Queue;
import java.util.Map;
import java.util.LinkedList;


/**
 * Provides a way to defer sending Firestore requests (such as document add/changes)
 * which typically are done using the document.collection().document().set() syntax.
 * A developer can instead use this class and set how many requests (n) they want to
 * be in the queue before they perform an upload to the database (of all the
 * requests, in bulk). They can also set a maximum delay they are willing to wait
 * for. So if after t seconds, there are less than n requests in the queue, then
 * we can go ahead and upload the requests we do have. This is so that the requests
 * in the queue won't be "stuck" waiting forever for more requests to come in 
 * to reach the threshold n.
 *
 */
public class LazyDatabaseUploader{

    private static final String TAG = "LazyDatabaseUploader";
    //This is the database that we upload to
    private FirebaseFirestore db;
    private Timer timer;
    //Even if n requests aren't in the queue, all queued requests will be uploaded once the timer value reaches maxDelay
    private long maxDelay;
    private Queue<LDURequest> queue;
    private int numRequestsInBundle;
    //When normal mode is enabled, LazyDatabaseUploader acts just like the normal database uploader
    //All of the optimizations are turned off when normalMode is true
    private boolean normalMode;
   
   
    public LazyDatabaseUploader()
    {
        db = null;
        timer = new Timer();
        numRequestsInBundle = 5;
        queue = new LinkedList<>();
        maxDelay = 5000;
        normalMode = false;
    }
   
    /**
    * Sets the working database for this class to link to the FirebaseFirestore object
    * that is passed in.
    *
    * @param ff an existing FirebaseFirestore object that we can upload our requests to
    */
    public void setDatabase(FirebaseFirestore ff)
    {
        if(ff != null) {
            db = ff;
        }
    }
   
    /**
    * Sets how many requests will be allowed in the queue before proceeding to
    * upload all of those requests. 
    *
    * Example Usage: 
    * LazyDatabaseUploader ldu = new LazyDatabaseUploader();
    * ldu.setNumRequestsInBundle(5);
    * ldu.set("myCollection", "myDocument", requestContent1)
    * ldu.set("myCollection", "myDocument", requestContent2)
    * ldu.set("myCollection", "myDocument", requestContent3)
    * ldu.set("myCollection", "myDocument", requestContent4)
    * ldu.set("myCollection", "myDocument", requestContent5)
    * 
    * At this point, since 5 requests have been put in the queue, the threshold
    * has been reached and we will execute all 5 requests (i.e. start uploading
    * to the database).
    * As a practical example, if you have a database that tracks log messages, then
    * after 5 log messages are generated locally you will upload all 5 at once to the
    * database.
    *
    * @param numReqs the maximum number of requests that will be allowed in the queue before performing a bulk upload of all the requests
    */
    public void setNumRequestsInBundle(int numReqs)
    {
        if(numReqs > 0) {
            numRequestsInBundle = numReqs;
        } else {
            // effectively: no optimization, just upload a request as soon as you get one in the queue
            numRequestsInBundle = 1;
        }
    }
    
    /**
    * Sets the maximum delay (in milliseconds) for the queue holding onto requests.
    * Example Usage: 
    * LazyDatabaseUploader ldu = new LazyDatabaseUploader();
    * ldu.setNumRequestsInBundle(5);
    * ldu.setMaxDelay(3000);
    * ldu.set("myCollection", "myDocument", requestContent1)
    * ldu.set("myCollection", "myDocument", requestContent2)
    *
    * So far you only have 2 requests in the queue, we set the numRequestsInBundle to be 5
    * to have a bundle and proceed with executing the requests (i.e. start uploading to the database).
    * Let's say it's been 3000 ms or 3 seconds since the first request was set, then
    * based on our maxDelay, those 2 requests would be uploaded anyway. As a practical
    * example, let's say you have a database that tracks log messages, and you locally
    * generated 2 log messages (L1 and L2) that you want to put in your database. You
    * want to wait until you have a total of 5 log messages, but let's say you don't want
    * to just wait forever, you are only willing to wait for 10 seconds and then you
    * want to upload whatever log messages you already have. Then you can set maxDelay
    * to be 10000 ms or 10 seconds.
    *
    *
    * @param ms the maximum amount of time you want requests to be buffered in the queue
    */
    public void setMaxDelay(long ms)
    {
        if(ms > 0)
        {
            maxDelay = ms;
        }
    }
   
    /** Starts a timer for "ms" number of milliseconds
    *
    * @param ms how long the timer should run for in milliseconds
    */
    private void startTimer(long ms)
    {
        System.out.println("Starting a timer for " + ms + " ms");
        timer = new Timer();
        timer.schedule(new UploadTask(), ms);
    }
   
    /** Stops the timer (i.e. call cancel() on the timer)*/
    private void stopTimer()
    {
        timer.cancel();
        System.out.println("Timer has been stopped");
    }
   
    /** Turn LazyDatabaseUploader into normal mode (no optimization) */
    public void enableNormalMode()
    {
        normalMode = true;
    }
   
   /** Turn LazyDatabaseUploader optimization back on */
    public void disableNormalMode()
    {
        normalMode = false;
    }
   
   
   /** 
   * Does a normal set (no optimizations, just plain db.collection().document.set() )
   * This method probably doesn't ever need to be called, it's just put here for the
   * developers convenience. It's most useful when you want to only use LazyDatabaseUploader
   * throughout your code but there are a handful of requests that are IMPORTANT or need to be
   * uploaded in real-time (such as data that affects the user's UI on a mobile app) and shouldn't
   * be delayed by waiting in the LazyDatabaseUploader's queue. ldu.normalSet() allows you
   * to use the same syntax as ldu.set() but it ensures your request goes through right away
   * to the database. 
   *
   * @param collection the collection in your database you want to add to
   * @param document the document in your database you want to create or update
   * @param requestInfo what data you actually want to add, this is the most important part of the request (passed as a Map of String to Object)
   */
   public void normalSet(String collection, String document, Map<String, Object> requestInfo)
   {
       if(collection == null || collection.length() == 0) {
            throw new IllegalArgumentException(
                String.format("Invalid collection, the collection name cannot be an empty string or null"));
       }
       else if(document == null || document.length() == 0) {
            throw new IllegalArgumentException(
                String.format("Invalid document, the document name cannot be an empty string or null"));
       }

       db.collection(collection).document(document)
        .set(requestInfo)
        .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d(TAG, "DocumentSnapshot successfully written!");
            }
        })
        .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w(TAG, "Error writing document", e);
            }
        });
   }
   
   /** 
   * Adds the request to a queue (optimized version) 
   *
   * @param collection the collection in your database you want to add to
   * @param document the document in your database you want to create or update
   * @param requestInfo what data you actually want to add, this is the most important part of the request (passed as a Map of String to Object)
   */
   public void set(String collection, String document, Map<String, Object> requestInfo)
   {
        if(collection == null || collection.length() == 0) {
            throw new IllegalArgumentException(
                String.format("Invalid collection, the collection name cannot be an empty string or null"));
        }
        else if(collection == null || document.isEmpty()) {
            throw new IllegalArgumentException(
                String.format("Invalid document, the document name cannot be an empty string or null"));
        }

       LDURequest r = new LDURequest(collection, document, requestInfo);

       if(normalMode)
       {
            queue.add(r);
            uploadAllRequestsInQueue();
       }
       else
       {
           try {
               queue.add(r);
               
               //The timer starts as soon as the queue receives its first object
               if(queue.size() == 1)
               {
                   startTimer(maxDelay);
               }
               
               
               if(queue.size() >= numRequestsInBundle) {
                   uploadAllRequestsInQueue();
                   stopTimer();
               }
           } catch(Exception e) {
               e.printStackTrace();
           }
        }
   }
   
   
   /** Take all the requests in the queue and upload them */
   public void uploadAllRequestsInQueue()
   {
        //Iterate over the queue
        while(!queue.isEmpty())
        {

            //Contains three private variables (collection, document, addition)
            LDURequest req = queue.peek();
           
            if(req == null)
            {
                //Ignore the request
            }
            else {
                //Unpack the request here
                String colName = req.getColName();
                String docName = req.getDocName();
                Map<String, Object> requestInfo = req.getContent();
               
                db.collection(colName).document(docName)
                        .set(requestInfo)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Log.d(TAG, "DocumentSnapshot successfully written!");
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.w(TAG, "Error writing document", e);
                            }
                        });
            }
           
            //Remove from the front of the queue
            queue.remove();
        }
   }
   
   
   
   
   /** Important! This is the TimerTask that will execute when the timer finishes, it uploads all the requests in the queue */
    class UploadTask extends TimerTask {
        public void run() {
            uploadAllRequestsInQueue();
            stopTimer(); //Terminate the timer thread
        }
    }

    // Example usage of the program
    /*public static void main(String[] args)
    {
        LazyDatabaseUploader ldu = new LazyDatabaseUploader();
        FirebaseFirestore db = new FirebaseFirestore();

        ldu.setDatabase(db);
        ldu.setNumRequestsInBundle(5);
        ldu.setMaxDelay(10000);

        Map<String, Object> requestInfo = null;
        ldu.set("Main", "New Document", requestInfo);
    }*/
}

