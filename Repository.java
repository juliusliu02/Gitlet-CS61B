package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static gitlet.Utils.*;


/** Represents a gitlet repository.
 *  This class handles all requests made to the repository.
 *  Responsible for manipulation of {@link Commit}s and {@link Blob}s.
 *  Handles errors.
 *
 *  @author Julius Liu
 */
@SuppressWarnings("PrimitiveArrayArgumentToVarargsMethod")
public class Repository implements Serializable, Dumpable {

    /** The current branch. */
    private String currentBranch;
    /** The current head. */
    private String currentHead;
    /** A pointer to an instance of current head commit.*/
    private transient Commit currentCommit;
    /** Map branch names to their head commits' hashes. */
    private final HashMap<String, String> branches;
    /** Stores staged files and their hashes.
     * Files are located within {@link #STAGE_DIR}.*/
    private final HashMap<String, String> stagedAdd;
    /** Stores files staged for removal. */
    private final HashSet<String> stagedDelete;

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The directories within .gitlet */
    public static final File META_DIR = join(GITLET_DIR, "repo");
    /** Stores objects. Files within this directory
     *  are addressed by hashes of their contents. */
    public static final File BLOB_DIR = join(GITLET_DIR, "blob");
    /** Stores internal {@link Commit} objects. Commits are addressed by hashes. */
    public static final File COMMIT_DIR = join(GITLET_DIR, "commit");
    public static final File STAGE_DIR = join(GITLET_DIR, "stage");

    /* Core IO */

    /** Creates a new Repository instance. */
    public Repository() {
        branches = new HashMap<>();
        stagedAdd = new HashMap<>();
        stagedDelete = new HashSet<>();
    }

    /** Initializes the repository with an initial commit under branch "master".
     *  Create necessary directories for internal objects. */
    public void init() {
        if (GITLET_DIR.isDirectory()) {
            message("A Gitlet version-control system already exists in the current directory.");
            return;
        }
        if (!GITLET_DIR.mkdir() || !BLOB_DIR.mkdir() || !COMMIT_DIR.mkdir() || !STAGE_DIR.mkdir()) {
            throw error("Couldn't initialize repository.");
        }
        currentBranch = "master";
        commit(Commit.init());
    }

    /** Returns the Repository instance in current working directory.
     *  Initialize transient pointer to current commit. */
    static Repository read() {
        Repository repo = readObject(META_DIR, Repository.class);
        repo.currentCommit = Commit.read(repo.currentHead);
        return repo;
    }

    /** Writes the Repository instance into file. */
    void write() {
        writeObject(META_DIR, this);
    }

    /* Commit */

    private void clearStage() {
        Blob.clearStage();
        stagedAdd.clear();
        stagedDelete.clear();
    }

    /** Takes a specific {@code commit} instance and writes it to file. */
    private void commit(Commit commit) {
        // Update currentHead.
        this.currentCommit = commit;
        this.currentHead = commit.write();
        branches.put(currentBranch, currentHead);
        // Commit all files.
        Blob.commitStagedFiles();
        clearStage();
    }

    /**
     * Commits current changes.
     * @param message A non-empty commit message.
     */
    public void commit(String message) {
        if (stagedAdd.isEmpty() && stagedDelete.isEmpty()) {
            message("No changes added to the commit.");
            return;
        }
        if (message.isBlank()) {
            message("Please enter a commit message.");
            return;
        }
        Commit commit = new Commit(message, branches.get(currentBranch), stagedAdd, stagedDelete);
        commit(commit);
    }

    /* Adding and removing files (Blobs) */

    /**
     * Adds file to staging area, waiting to add to the next commit.
     * <br>
     * Does not stage a file when file doesn't exist or file is already tracked.
     * If file is staged for removal, un-removes the file.
     */
    public void add(String filename) {
        // Fetch file and add it to staging area.
        File file = join(CWD, filename);
        // Failure: File does not exist.
        if (!file.exists()) {
            message("File does not exist.");
            return;
        }
        // File is already tracked.
        if (currentCommit.contains(filename, Blob.hash(file))) {
            return;
        }
        // Un-remove the file if it's staged for removal.
        if (stagedDelete.remove(filename)) {
            return;
        }
        // Stage file.
        stagedAdd.put(filename, Blob.stageBlob(file));
    }

    /**
     * Stages the file for removal.
     * <br>
     * If the file is currently staged for addition, untrack the file.
     */
    public void remove(String filename) {
        // stagedAdd.remove returns null when it's not staged.
        if (stagedAdd.containsKey(filename)) {
            // Effectively making the file untracked without deleting it.
            Blob.unstageBlob(stagedAdd.remove(filename));
        } else if (currentCommit.getFileHash(filename) != null) {
            stagedDelete.add(filename);
            // Delete the file.
            restrictedDelete(filename);
        } else {
            message("No reason to remove the file.", filename);
        }
    }

    /* Logging */

    /**
     * Starting at the current head commit, displays information
     * about itself and all parent commits in reversed chronological order.
     */
    public void log() {
        Commit pointer = Commit.read(branches.get(currentBranch));
        while (pointer != null) {
            pointer.dump();
            pointer = pointer.getParentCommit();
        }
    }

    /** Displays information about each commit in reversed order,
     *  starting at the current head commit. */
    public void globalLog() {
        List<String> commits = plainFilenamesIn(COMMIT_DIR);
        for (String commit : commits) {
            Commit.read(commit).dump();
        }
    }

    /* Checkout */
    /**
     * A helper function for checking out a file in a particular commit.
     * */
    private void checkoutFile(String filename, Commit commit) {
        File f = commit.getFile(filename);
        if (f == null) {
            message("File does not exist in that commit.");
            return;
        }
        writeContents(join(CWD, filename), readContents(f));
    }

    public void checkoutFile(String filename) {
        checkoutFile(filename, currentCommit);
    }

    public void checkoutFile(String filename, String commitID) {
        Commit commit = Commit.read(commitID);
        if (commit == null) {
            message("No commit with that id exists.");
            return;
        }
        checkoutFile(filename, commit);
    }

    public void checkoutBranch(String branch) {
        if (!branches.containsKey(branch)) {
            message("No such branch exists.");
        } else if (currentBranch.equals(branch)) {
            message("No need to checkout the current branch.");
        } else {
            String checkoutID = branches.get(branch);
            Commit checkoutCommit = Commit.read(checkoutID);
            Blob.safeClearCWD(currentCommit);
            checkoutCommit.restore();
            clearStage();
            currentBranch = branch;
            currentCommit = checkoutCommit;
            currentHead = checkoutID;
        }
    }

    public void makeBranch(String branch) {
        if (branches.putIfAbsent(branch, currentHead) != null) {
            message("A branch with that name already exists.", branch);
        }
        branches.put(branch, currentHead);
    }

    public void removeBranch(String branch) {
        if (currentBranch.equals(branch)) {
            message("Cannot remove the current branch.");
        } else if (branches.remove(branch) == null) {
            message("A branch with that name does not exist.");
        }
    }

    public void reset(String hash) {
        Commit newCommit = Commit.read(hash);
        if (newCommit == null) {
            message("No commit with that id exists.");
            return;
        }
        // Remove any staged files.
        for (String s : stagedAdd.keySet()) {
            File f = join(CWD, s);
            if (f.exists() && Objects.equals(stagedAdd.get(s), sha1(readContents(f)))) {
                f.delete();
            }
        }
        // Clear all tracked files.
        Blob.safeClearCWD(currentCommit);
        newCommit.restore();
        currentCommit = newCommit;
        currentHead = newCommit.getHash();
        branches.put(currentBranch, currentHead);
        clearStage();
    }

    public void dump() {
        System.out.println("Current branch: " + currentBranch);
        System.out.println("Current head: " + currentHead);
        System.out.println("Current branches: " + branches);
        System.out.println("Current staged files: " + stagedAdd);
    }

    public void find(String commitMsg) {
        List<String> commits = plainFilenamesIn(COMMIT_DIR);
        StringBuilder ids = new StringBuilder();
        for (String hash : commits) {
            Commit commit = Commit.read(hash);
            if (commit.getMessage().equals(commitMsg)) {
                ids.append(commit.getHash());
                ids.append(System.lineSeparator());
            }
        }
        if (!ids.isEmpty()) {
            System.out.println(ids);
        } else {
            message("Found no commit with that message.");
        }
    }

    public void status() {
        System.out.println("=== Branches ===");
        branches.keySet().stream().sorted().forEachOrdered(branch -> {
            if (branch.equals(currentBranch)) {
                System.out.println('*' + branch);
            } else {
                System.out.println(branch);
            }
        });
        System.out.println();
        System.out.println("=== Staged Files ===");
        stagedAdd.keySet().stream().sorted().forEachOrdered(System.out::println);
        System.out.println();
        System.out.println("=== Removed Files ===");
        stagedDelete.stream().sorted().forEachOrdered(System.out::println);
        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println();
        System.out.println("=== Untracked Files ===");
        System.out.println();
    }

    private void mergeBranch(String branch, String mergeParent) {
        String msg = "Merged " + branch + " into " + currentBranch + ".";
        Commit commit = new Commit(msg, currentHead, mergeParent, stagedAdd, stagedDelete);
        commit(commit);
    }

    /** Determine the latest version of files and
     *  stage them if they don't belong to {@link #currentCommit}. */
    private void mergeFile(String file, Commit base, Commit merge) {
        File baseFile = base.getFile(file);
        String baseHash = Blob.hash(baseFile);
        File currentFile = currentCommit.getFile(file);
        String currentHash = Blob.hash(currentFile);
        File mergeFile = merge.getFile(file);
        String mergeHash = Blob.hash(mergeFile);
        if (Objects.equals(currentHash, mergeHash) || Objects.equals(baseHash, mergeHash)) {
            // currentCommit has the latest version.
            return;
        } else if (Objects.equals(baseHash, currentHash)) {
            // mergeCommit has latest.
            if (Objects.equals(mergeHash, null)) {
                remove(file);
            } else {
                checkoutFile(file, merge);
                add(file);
            }
        } else {
            message("Encountered a merge conflict.");
            StringBuilder sb = new StringBuilder();
            sb.append("<<<<<<< HEAD");
            sb.append(System.lineSeparator());
            if (currentFile != null && currentFile.exists()) {
                sb.append(readContentsAsString(currentFile));
            }
            sb.append("=======");
            sb.append(System.lineSeparator());
            if (mergeFile != null && mergeFile.exists()) {
                sb.append(readContentsAsString(mergeFile));
            }
            sb.append(">>>>>>>");
            sb.append(System.lineSeparator());
            writeContents(join(CWD, file), sb.toString());
            add(file);
        }
    }

    public void merge(String branch) {
        if (!stagedAdd.isEmpty() || !stagedDelete.isEmpty()) {
            message("You have uncommitted changes.");
            return;
        }
        if (branch.equals(currentBranch)) {
            message("Cannot merge a branch with itself.");
            return;
        }
        String mergeHeadHash = branches.get(branch);
        if (mergeHeadHash == null) {
            message("A branch with that name does not exist.");
            return;
        }
        // Chose not to use safeClearCWD to avoid reading and writing all files.
        if (!Blob.containsAllFilesInCWD(currentCommit)) {
            message("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
            return;
        }

        Commit mergeCommit = Commit.read(mergeHeadHash);
        Commit base = Commit.getMergeBase(currentCommit, mergeCommit);
        // mergeCommit is an ancestor of currentCommit.
        if (base.equals(mergeCommit)) {
            message("Given branch is an ancestor of the current branch.");
            return;
        } else if (base.equals(currentCommit)) {
            // currentCommit is an ancestor of mergeCommit.
            checkoutBranch(branch);
            message("Current branch fast-forwarded.");
            mergeBranch(branch, mergeHeadHash);
            return;
        }

        HashSet<String> files = new HashSet<>();
        files.addAll(currentCommit.getAllFiles());
        files.addAll(mergeCommit.getAllFiles());
        for (String file : files) {
            mergeFile(file, base, mergeCommit);
        }
        mergeBranch(branch, mergeCommit.getHash());
    }
}
