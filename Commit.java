package gitlet;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static gitlet.Utils.*;

/** Represents a gitlet commit object.
 *  Keep tracks of all the metadata and provides getter methods.
 *
 *  @author Julius Liu
 */
@SuppressWarnings("PrimitiveArrayArgumentToVarargsMethod")
public class Commit implements Serializable, Dumpable {
    /** A formatter for printing the timestamp. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER
            = DateTimeFormatter.ofPattern("E LLL d HH:mm:ss uuuu Z");

    /** A descriptive commit message. */
    private final String message;
    /** A timestamp of the commit. */
    private final ZonedDateTime time;
    /** The hash of its parent. */
    private final String parent;
    /** The hash of its merge parent. */
    // For non-merge nodes, this variable will be null.
    private final String mergeParent;
    // Example: Thu Nov 9 20:00:05 2017 -0800
    /** Maps filenames to blob references. */
    // A HashMap will not work here since the order
    // of its contents is non-deterministic.
    private final TreeMap<String, String> files;

    /* File IO */
    /** Returns the commit instance with the specific ID. Returns null when commit doesn't exist. */
    static Commit read(String commitID) {
        if (commitID.length() == UID_LENGTH) {
            if (join(Repository.COMMIT_DIR, commitID).exists()) {
                return readObject(join(Repository.COMMIT_DIR, commitID), Commit.class);
            } else {
                return null;
            }
        } else if (commitID.length() < UID_LENGTH) {
            for (String filename : plainFilenamesIn(Repository.COMMIT_DIR)) {
                if (filename.startsWith(commitID)) {
                    return readObject(join(Repository.COMMIT_DIR, filename), Commit.class);
                }
            }
            throw error("File does not exist in that commit.", commitID);
        } else {
            message("Illegal commit ID: " + commitID);
            return null;
        }
    }

    /** Writes the Repository instance into file and return its hash. */
    String write() {
        String hash = getHash();
        writeObject(join(Repository.COMMIT_DIR, hash), this);
        return hash;
    }

    /** Constructor that takes a message, its parent, and stagedFile. */
    public Commit(String message, String parent,
                  Map<String, String> stagedAdd, Set<String> stagedDelete) {
        this.message = message;
        this.time = ZonedDateTime.now();
        this.parent = parent;
        this.mergeParent = null;

        Commit parentCommit = read(parent);
        this.files = new TreeMap<>();
        this.files.putAll(parentCommit.files);
        this.files.putAll(stagedAdd);
        /* keySet returns a Set view of the keys contained in this map.
         * The set is backed by the map, so changes to the map are
         * reflected in the set, and vice-versa. */
        this.files.keySet().removeAll(stagedDelete);
    }

    /** Constructor for merge commits. */
    public Commit(String message, String parent, String mergeParent,
                  Map<String, String> stagedAdd, Set<String> stagedDelete) {
        this.message = message;
        this.time = ZonedDateTime.now();
        this.parent = parent;
        this.mergeParent = mergeParent;

        Commit parentCommit = read(parent);
        this.files = new TreeMap<>();
        this.files.putAll(parentCommit.files);
        this.files.putAll(stagedAdd);
        this.files.keySet().removeAll(stagedDelete);
    }

    /** Private constructor for initial commit.*/
    private Commit() {
        this.message = "initial commit";
        this.time = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.systemDefault());
        this.files = new TreeMap<>();
        this.parent = null;
        this.mergeParent = null;
    }

    /** Static method for initial commit. */
    public static Commit init() {
        return new Commit();
    }

    /**
     * Print useful information about this object on System.out.
     */
    public void dump() {
        System.out.println("===");
        System.out.println("commit " + getHash());
        if (mergeParent != null) {
            System.out.println("Merge: " + abbreviateHash(parent)
                    + " " + abbreviateHash(mergeParent));
        }
        System.out.println("Date: " + DATE_TIME_FORMATTER.format(time));
        System.out.println(message);
        System.out.println();
    }

    /** Returns the parent Commit of the instance. Returns {@code null} if empty. */
    public Commit getParentCommit() {
        if (this.parent == null) {
            return null;
        } else {
            return read(parent);
        }
    }

    /* Access methods */

    /**
     * Returns the corresponding version of the file tracked in this commit.
     * Returns {@code null} if file doesn't exist.
     */
    public File getFile(String filename) {
        String hash = files.get(filename);
        if (hash == null) {
            return null;
        }
        return Blob.readBlob(hash);
    }

    /** Returns the hash of a particular file tracked in this commit. */
    public String getFileHash(String filename) {
        return files.get(filename);
    }

    /** Returns a Set containing all the names of files this commit tracks. */
    public Set<String> getAllFiles() {
        // return a copy of keySet to avoid mutation.
        return new HashSet<>(files.keySet());
    }

    /** Returns hash of this commit. */
    public String getHash() {
        return sha1(serialize(this));
    }

    /** Returns the message of this commit. */
    public String getMessage() {
        return this.message;
    }

    /** Returns if the current commit is tracking the exact version of file. */
    public boolean contains(String file, String hash) {
        return Objects.equals(files.get(file), hash);
    }

    /** Reset the state of CWD to be this commit. */
    public void restore() {
        Blob.restoreFiles(files);
    }

    /** Returns an abbreviated hash with only the first seven letters. */
    private String abbreviateHash(String hash) {
        return hash.substring(0, 7);
    }

    /* Merge */

    /** Return a list of all parent commits in reversed chronological order. */
    private static TreeMap<ZonedDateTime, Commit> getCommitHistory(Commit commit) {
        // Refactored out the recursive step to reduce overhead of sorting.
        Set<Commit> ancestors = getAllAncestors(new HashSet<>(), commit);
        // LinkedHashMap return things in insertion order.
        TreeMap<ZonedDateTime, Commit> map = new TreeMap<>();
        /* Sort commits in reversed chronological order.
         * The comparison of ZonedDateTime is based first on the instant,
         * then on the local date-time, then on the zone ID, then on the chronology. */
        ancestors.stream().sorted(Comparator.comparing((Commit c) -> c.time).reversed()).
                forEach(c -> map.put(c.time, c));
        return map;
    }

    /** Returns a {@link Set<Commit>} of all ancestors of a commit. */
    private static Set<Commit> getAllAncestors(Set<Commit> ancestors, Commit pointer) {
        while (true) {
            ancestors.add(pointer);
            if (pointer.mergeParent != null) {
                // Recursively add parents of merge commits.
                getAllAncestors(ancestors, Commit.read(pointer.mergeParent));
            }
            if (pointer.parent == null) {
                break;
            }
            pointer = Commit.read(pointer.parent);
        }
        return ancestors;
    }

    /** Returns the latest common ancestor of two commits as the base commit for merging. */
    public static Commit getMergeBase(Commit base, Commit merge) {
        TreeMap<ZonedDateTime, Commit> baseHistory = getCommitHistory(base);
        TreeMap<ZonedDateTime, Commit> mergeHistory = getCommitHistory(merge);
        if (mergeHistory.containsValue(base)) {
            return base;
        }
        if (baseHistory.containsValue(merge)) {
            return merge;
        }

        // Remove the latest entry while we don't see a common ancestor.
        Map.Entry<ZonedDateTime, Commit> latestBase = baseHistory.pollLastEntry();
        Map.Entry<ZonedDateTime, Commit> latestMerge = mergeHistory.pollLastEntry();
        while (!Objects.equals(latestBase.getValue().getHash(), latestMerge.getValue().getHash())) {
            if (latestBase.getKey().isAfter(latestMerge.getKey())) {
                latestBase = baseHistory.pollLastEntry();
            } else {
                latestMerge = mergeHistory.pollLastEntry();
            }
        }
        return latestBase.getValue();
    }

    /* Commit comparing logic */
    @Override
    public boolean equals(Object other) {
        if (other instanceof Commit) {
            Commit o = (Commit) other;
            return Objects.equals(this.getHash(), o.getHash());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.getHash().hashCode();
    }
}
