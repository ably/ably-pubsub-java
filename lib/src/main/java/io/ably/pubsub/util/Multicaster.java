package io.ably.pubsub.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection of members who are listeners, with methods that are safe to be called from any thread.
 * @param <T> The type of elements being added to this multicaster - the listeners.
 */
public abstract class Multicaster<T> {
    private final List<T> members = new ArrayList<>();

    public Multicaster(T... members) { for(T m : members) this.members.add(m); }

    public synchronized void add(T member) { members.add(member); }
    public synchronized void remove(T member) { members.remove(member); }
    public synchronized void clear() { members.clear(); }
    public synchronized boolean isEmpty() { return members.isEmpty(); }
    public synchronized int size() { return members.size(); }

    /**
     * Returns a snapshot of the members of this multicaster instance.
     */
    protected synchronized List<T> getMembers() {
        return new ArrayList<>(members);
    }
}
