package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static gitlet.Repository.*;
import static gitlet.Utils.*;

/** A library for reading and writing blobs to files.
 *  Manages the blob area and staging area.
 *  All read methods take a directory File and a hash, and return a {@link File},
 *  and all write methods takes a directory and a {@link File}, and return the file's hash.
 *  @author Julius Liu */
@SuppressWarnings("PrimitiveArrayArgumentToVarargsMethod")
public final class Blob {

    /** Adds a file to staging area, returns its hash. */
    static String stageBlob(File src) {
        byte[] data = readContents(src);
        String hash = sha1(data);
        // Avoid duplicates.
        if (join(BLOB_DIR, hash).exists()) {
            return hash;
        }
        File dest = join(Repository.STAGE_DIR, hash);
        writeContents(dest, data);
        return hash;
    }

    /** Deletes a file from staging area using its hash. */
    public static void unstageBlob(String hash) {
        join(Repository.STAGE_DIR, hash).delete();
    }

    /** Clears all files in staging area. */
    static void clearStage() {
        for (String file : plainFilenamesIn(STAGE_DIR)) {
            if (!join(STAGE_DIR, file).delete()) {
                throw error("Internal error clearing staging area.");
            }
        }
    }

    /** Commits all files in staging area to {@link Repository#BLOB_DIR}.*/
    static void commitStagedFiles() {
        for (String file : plainFilenamesIn(STAGE_DIR)) {
            try {
                Files.move(join(STAGE_DIR, file).toPath(), join(BLOB_DIR, file).toPath());
            } catch (IOException e) {
                throw error("Internal error committing staged files: " + file, e.getMessage());
            }
        }
    }

    /** Given a file's hash, fetch it from the directory. */
    static File readBlob(String name) {
        File file = join(BLOB_DIR, name);
        if (file.exists()) {
            return file;
        } else {
            throw error("File doesn't exist.", name);
        }
    }

    /** Clear CWD if and only if all files are tracked in current commits. */
    static void safeClearCWD(Commit commit) {
        if (containsAllFilesInCWD(commit)) {
            List<String> files = plainFilenamesIn(CWD);
            files.forEach(Utils::restrictedDelete);
        } else {
            message("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
            System.exit(0);
        }
    }

    /** Return true if and only if all files are */
    static boolean containsAllFilesInCWD(Commit commit) {
        List<String> files = plainFilenamesIn(CWD);
        for (String s : files) {
            File file = join(CWD, s);
            if (!Objects.equals(commit.getFileHash(s), hash(file))) {
                return false;
            }
        }
        return true;
    }

    /** Overwrites the current directory to contains only files recorded in the Map. */
    static void restoreFiles(Map<String, String> files) {
        files.forEach((key, value) -> {
            try {
                Files.copy(join(BLOB_DIR, value).toPath(), join(CWD, key).toPath());
            } catch (IOException e) {
                throw error("Internal error restoring commit.", e.getMessage());
            }
        });
    }

    static String hash(File f) {
        if (f == null || !f.exists()) {
            return null;
        }
        return sha1(readContents(f));
    }
}
