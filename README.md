# Gitlet Design Document

## Classes and Data Structures

### Repository

`Repository` initiates all requests. It is responsible for managing `Commit`s and `Object`s.

The following operations, which make up most of the commands in `gitlet`, are repository-based:
- initializing repo: `init`;
- tracking files: `add`, `remove`;
- branching: `checkout`, `branch`, `merge`;
- status checking: `log`, `global-log`, `status`;
- and `reset`.

#### Fields

1. String `currentBranch`: tracks the current branch.
2. String `currentHead`: tracks the hash of current head commit.
3. Commit `currentCommit`: a pointer to the current head commit instance. Marked `transient` to exclude it from serialization.
4. HashMap<String, String> `branches`: maps branch names to their head.
5. HashMap<String, String> `stagedAdd`: tracks staged addition.
6. HashSet<String> `stagedDelete`: tracks staged deletion.

### Commit

`Commit` keep tracks of all the metadata, including a message and a timestamp, as well as a mapping from filenames to their contents tracked in the particular commit.
All fields are `final` to avoid any mutation.

`Commit` encapsulates all data and provides a range of public methods, consisting of
- two constructors for normal commits and merge commits respectively:`Commit()` ;
- one public method for resetting directory to committed status: `restore()`;
- getter methods: `getParentCommit()`, `getFile()`, `getFileHash()`, `getAllFiles()`, `getHash()`, `getMessage()`, `contains()`;
- and a private helper method for merging branches: `getMergeBase`.

`equals()` and `hashCode()` are overridden to compare `Commit`s by their contents.

#### Fields

1. String `message`: a descriptive commit message.
2. ZonedDateTime `time`: a timestamp of the commit.
3. String `parent`: the hash of its parent.
4. String `mergeParent`: the hash of the other parents if it's a merge commit. `null` otherwise.
5. TreeMap<String, String> `files`: maps filenames to blob references.
    - Note that a HashMap does not produce a consistent hash value—which is required to identify the commit—and is therefore replaced by a TreeMap.

### Blob

`Blob` is a library class with no instance variable. It realizes persistence in this project and manages blobs and staging areas.

It provides the following I/O functionalities for file manipulation:
- stage and un-stage a file: `stageBlob()`, `unstageBlob()`;
- committing staged files and clear stage: `commitStagedFiles()`, `clearStage()`;
- hashing a file: `hash()`;
- fetching files by its hash: `readBlob()`;
- ensuring every file is tracked: `containsAllFilesInCWD()`;
- clearing and resetting current working directory: `safeClearCWD()`, `restoreFiles()`.

## Algorithms

### `add` and `remove`

Changes in the directory are tracked in `stagedAdd` and `stagedDelete` and finalized only when the user calls `gitlet commit`.

Adding a file behaves differently depending on context:
1. If the file doesn't exist: return.
2. If the same version of the file is found in the current commit: return.
3. If the file is staged for removal: "un-remove" the file.
4. Default behaviour: stage the file for addition and copy its contents into staging area.

`remove` is the inverse of `add`:
1. If the file doesn't exist: notify the user that there is no reason to remove the file.
2. If it's staged for addition: "un-stage" the file.
3. Default behaviour: stage the file for addition and remove the file from working directory if the user hasn't already done so.

### `commit`

To create a `Commit`, a commit message from the user is required.

When `gitlet commit` is called, the repo then passes a few parameters to the `Commit` constructor: a commit message, the hash of current commit, and staged changes.

After the instantiation, the repo writes the commit to file, updates its variable tracking current head commits, moves staged files into `blob` folder, and clears the stage.

### `checkout`

The user can check out a file in current commit, a file in a past commit, or check out a branch. **`checkout` will modify the current working directory and might overwrite any unsaved changes.**

Checking out a file means to restore a file to its previous state tracked in a particular commit. On the other hand, checking out a branch tells the repo to point its current head to the latest commit on that branch and reset files in the directory accordingly.

### `branch`

A HashMap is stored in the `Repository` instance to map branches to their latest commits. Commits’ hashes are stored in place of the actual objects to avoid serializing the commits as well.

When the user creates a new branch, a new record is put into `branches` mapping the branch name to the current head commit.

### `merge`

In this implementation, `merge` is broken down into two sub-tasks: finding the merge base and comparing files.

#### Finding the merge base

The logic is implemented in `Commit` class as a static method, which takes two commits and return the latest common ancestor.

The program does this by listing all parents of the two commits and their parents recursively in reversed chronological order, and find the latest common parent.

#### Comparing files

Given `base`, `current`, and `merge` commits, the program then compares every file tracked in `current` and `merge` individually to fetch the latest version of the file.

Comparing a file can have one of the following outcomes:
- File is the same in `current` and `merge`: it is considered latest.
- File is the same in `base` and one of  `current` or `merge`: the other version is considered latest.
- File is different in all three commits: a merge conflict occurred.

The program runs the checks for every file and, for each of them, the latest version will be fetched to current working directory. When the merge is finished, the repo automatically `add` all files and `commit` the changes.

## Persistence

Persistence for internal objects like `Repository` and `Commit` are realized by serialization. For those objects, a static `read` method and a `write` method are provided to standardize the serialization.

### `Repository`

The `Repository` instance is saved in `./gitlet/meta`. Since there is only one repo per directory, `Repository.read()` doesn’t need any additional arguments.

`write()` simply serializes itself and saves the result to the defined path.

### `Commit`

`Commit`s, however, are only addressable by their hashes. `Commit.read()` therefore takes a String `hash` as an argument and search for a commit with the given hash in `./gitlet/commit`.

Due to the length of hashes, `Commit.read()` also accepts an abbreviated hash value, where it will return the first commit it encounters with a hash that starts with the given value. Unfortunately, one thing to note about this behaviour is that the result is not deterministic when two or more commits include the same starting characters in the argument.

When `write` for a commit is called, the commit serializes and stores itself in a file named its hash under `./gitlet/commit` . Upon success, it returns the hash as a String.

### Objects

When creating objects, the contents are simply copied and saved in a file. The files also use their hashes as the filenames.
