package io.ably.pubsub.http;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ErrorInfo;

/**
 * A high level wrapper of both a sync and an async HttpScheduler.
 */
public class Http implements AutoCloseable {
    private final AsyncHttpScheduler asyncHttp;
    private final SyncHttpScheduler syncHttp;

    public Http(AsyncHttpScheduler asyncHttp, SyncHttpScheduler syncHttp) {
        this.asyncHttp = asyncHttp;
        this.syncHttp = syncHttp;
    }

    @Override
    public void close() throws Exception {
        asyncHttp.close();
    }

    public void connect() {
        asyncHttp.connect();
    }

    /**
     * [Internal Method]
     * <p>
     * We use this method to implement proxy Realtime / HTTP clients that add additional data to the underlying client.
     */
    public Http exchangeHttpCore(HttpCore httpCore) {
        return new Http(asyncHttp.exchangeHttpCore(httpCore), new SyncHttpScheduler(httpCore));
    }

    public class Request<Result> {
        private final Execute<Result> execute;

        Request(Execute<Result> execute) {
            this.execute = execute;
        }

        public Result sync() throws AblyException {
            final SyncExecuteResult<Result> result = new SyncExecuteResult<>();
            execute.execute(Http.this.syncHttp, new Callback<Result>() {
                @Override
                public void onSuccess(Result r) {
                    result.ok = r;
                }

                @Override
                public void onError(ErrorInfo e) {
                    result.error = e;
                }
            });
            if (result.error != null) {
                throw AblyException.fromErrorInfo(result.error);
            }
            return result.ok;
        }

        public void async(Callback<Result> callback) {
            try {
                execute.execute(Http.this.asyncHttp, callback);
            } catch (AblyException e) {
                callback.onError(e.errorInfo);
            }
        }
    }

    public <Result> Request<Result> request(Execute<Result> execute) {
        return new Request(execute);
    }

    public <Result> Request<Result> failedRequest(final AblyException e) {
        return new Request(new Execute<Result>() {
            @Override
            public void execute(HttpScheduler http, final Callback<Result> callback) throws AblyException {
                //throw e;
                http.execute(new Runnable() {
                @Override
                public void run() {
                    callback.onError(e.errorInfo);
                }
                });
            }
        });
    }

    public interface Execute<Result> {
        void execute(HttpScheduler http, Callback<Result> callback) throws AblyException;
    }

    private static class SyncExecuteResult<Result> {
        public Result ok = null;
        public ErrorInfo error = null;
    }
}
