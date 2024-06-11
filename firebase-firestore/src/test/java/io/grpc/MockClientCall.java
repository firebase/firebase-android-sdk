package io.grpc;

import android.util.Pair;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import java.util.ArrayList;
import java.util.List;

public class MockClientCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {

    private int sentIndex = -1;
    private List<TaskCompletionSource<ReqT>> sent = new ArrayList<>();
    private TaskCompletionSource<Pair<Listener<RespT>, Metadata>> startTask = new TaskCompletionSource<>();
    private TaskCompletionSource<Pair<String, Throwable>> cancelTask = new TaskCompletionSource<>();

    @Override
    public void start(Listener<RespT> responseListener, Metadata headers) {
        startTask.setResult(Pair.create(responseListener, headers));
        System.out.println(">>> start");
    }

    @Override
    public void request(int numMessages) {
        System.out.println(">>> request");
    }

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
        cancelTask.setResult(Pair.create(message, cause));
        System.out.println(">>> cancel");
    }

    @Override
    public void halfClose() {
        System.out.println(">>> halfClose");
    }

    @Override
    public void sendMessage(ReqT message) {
        System.out.println(">>> sendMessage");
        final TaskCompletionSource<ReqT> sourceTask;
        synchronized (sent) {
            sentIndex++;
            if (sent.size() > sentIndex) {
                sourceTask = sent.get(sentIndex);
            } else {
                sourceTask = new TaskCompletionSource<>();
                sent.add(sourceTask);
            }
        }
        sourceTask.setResult(message);
    }

    public Task<Pair<Listener<RespT>, Metadata>> getStart() {
        return startTask.getTask();
    }

    public Task<Pair<String, Throwable>> getCancel() {
        return cancelTask.getTask();
    }

    public Task<ReqT> getSent(int index) {
        synchronized (sent) {
            while (sent.size() <= index) {
                sent.add(new TaskCompletionSource<>());
            }
            return sent.get(index).getTask();
        }
    }
}
