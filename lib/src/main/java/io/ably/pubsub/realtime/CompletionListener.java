package io.ably.pubsub.realtime;

import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ErrorInfo;

/**
 * An interface allowing a client to be notified of the outcome
 * of an asynchronous operation.
 */
public interface CompletionListener {
    /**
     * Called when the associated operation completes successfully.
     */
    void onSuccess();

    /**
     * Called when the associated operation completes with an error.
     * @param reason information about the error.
     */
    void onError(ErrorInfo reason);

    /**
     * A Multicaster instance is used in the Ably library to manage a list
     * of client listeners against certain operations.
     */
    class Multicaster extends io.ably.pubsub.util.Multicaster<CompletionListener> implements CompletionListener {
        public Multicaster(CompletionListener... members) { super(members); }

        @Override
        public void onSuccess() {
            for (final CompletionListener member : getMembers())
                try {
                    member.onSuccess();
                } catch(Throwable t) {}
        }

        @Override
        public void onError(ErrorInfo reason) {
            for (final CompletionListener member : getMembers())
                try {
                    member.onError(reason);
                } catch(Throwable t) {}
        }
    }

    class ToCallback<T> implements Callback<T> {
        private CompletionListener listener;
        public ToCallback(CompletionListener listener) {
            this.listener = listener;
        }

        @Override
        public void onSuccess(T v) {
            listener.onSuccess();
        }

        @Override
        public void onError(ErrorInfo reason) {
            listener.onError(reason);
        }
    }

    class FromCallback implements CompletionListener {
        private final Callback<Void> callback;

        public FromCallback(Callback<Void> callback) {
            this.callback = callback;
        }

        @Override
        public void onSuccess() {
            callback.onSuccess(null);
        }

        @Override
        public void onError(ErrorInfo reason) {
            callback.onError(reason);
        }
    }
}
