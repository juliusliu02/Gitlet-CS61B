package gitlet;

import static gitlet.Repository.GITLET_DIR;
import static gitlet.Utils.message;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Julius Liu
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ...
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            message("Please enter a command.");
            return;
        }
        String firstArg = args[0];
        final Repository repo;
        // Ensures repo is valid.
        if (firstArg.equals("init")) {
            repo = new Repository();
            repo.init();
            repo.write();
            return;
        } else if (!GITLET_DIR.exists()) {
            message("Not in an initialized Gitlet directory.");
            return;
        }
        repo = Repository.read();
        switch (firstArg) {
            case "add" -> {
                if (args.length != 2) {
                    message("Please enter a filename.");
                    return;
                }
                repo.add(args[1]);
            }
            case "rm" -> {
                if (args.length != 2) {
                    message("Please enter a filename.");
                    return;
                }
                repo.remove(args[1]);
            }
            case "commit" -> {
                if (args.length != 2) {
                    message("Please enter a commit message.");
                    return;
                }
                repo.commit(args[1]);
            }
            case "log" -> {
                if (args.length != 1) {
                    message("Incorrect number of arguments.");
                    return;
                }
                repo.log();
            }
            case "global-log" -> {
                if (args.length != 1) {
                    message("Incorrect number of arguments.");
                    return;
                }
                repo.globalLog();
            }
            case "status" -> {
                if (args.length != 1) {
                    message("Incorrect number of arguments.");
                    return;
                }
                repo.status();
            }
            case "branch" -> {
                if (args.length != 2) {
                    message("Please enter a branch name.");
                    return;
                }
                repo.makeBranch(args[1]);
            }
            case "rm-branch" -> {
                if (args.length != 2) {
                    message("Please enter a branch name.");
                    return;
                }
                repo.removeBranch(args[1]);
            }
            case "checkout" -> {
                if (args.length == 2) {
                    repo.checkoutBranch(args[1]);
                } else if (args.length == 3 && args[1].equals("--")) {
                    repo.checkoutFile(args[2]);
                } else if (args.length == 4 && args[2].equals("--")) {
                    repo.checkoutFile(args[3], args[1]);
                } else {
                    message("Incorrect operands.");
                    return;
                }
            }
            case "find" -> {
                if (args.length != 2) {
                    message("Please enter a commit message.");
                    return;
                }
                repo.find(args[1]);
            }
            case "reset" -> {
                if (args.length != 2) {
                    message("Please enter a commit hash.");
                }
                repo.reset(args[1]);
            }
            case "merge" -> {
                if (args.length != 2) {
                    message("Incorrect operands.");
                }
                repo.merge(args[1]);
            }
            default -> message("No command with that name exists.");
        }
        repo.write();
    }

}
