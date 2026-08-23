package jp.yuya.dev.developerdungeon.runner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;

final class RunnerSnapshotReader {
    private final DockerGateway docker;
    private final Duration timeout;
    private final String stageFourPath;
    private final String stageFiveC0;
    private final String stageFiveC1;
    private final String stageFiveC1Tree;

    RunnerSnapshotReader(DockerGateway docker, Duration timeout, String stageFourPath,
            String stageFiveC0, String stageFiveC1, String stageFiveC1Tree) {
        this.docker = docker;
        this.timeout = timeout;
        this.stageFourPath = stageFourPath;
        this.stageFiveC0 = stageFiveC0;
        this.stageFiveC1 = stageFiveC1;
        this.stageFiveC1Tree = stageFiveC1Tree;
    }

    RepositorySnapshot read(String containerId, String stageKey) {
        String head = gitOutput(containerId, List.of("rev-parse", "HEAD")).trim();
        String tree = gitOutput(containerId, List.of("rev-parse", "HEAD^{tree}")).trim();
        String parents = gitOutput(containerId, List.of("show", "-s", "--format=%P", "HEAD")).trim();
        String firstParentTree = parents.isBlank() ? "" : gitOutput(containerId, List.of("rev-parse", "HEAD^1^{tree}")).trim();
        String ancestors = gitOutput(containerId, List.of("rev-list", "HEAD")).trim();
        String status = gitOutput(containerId, List.of("status", "--porcelain=v1")).trim();
        var parentList = parents.isBlank() ? List.<String>of() : List.of(parents.split(" "));
        var ancestorList = ancestors.isBlank() ? List.<String>of() : List.of(ancestors.split("\\R"));
        boolean reverting = stateFileExists(containerId, "REVERT_HEAD");
        boolean cherryPicking = stateFileExists(containerId, "CHERRY_PICK_HEAD");
        boolean merging = stateFileExists(containerId, "MERGE_HEAD");
        boolean rebasing = stateFileExists(containerId, "rebase-merge") || stateFileExists(containerId, "rebase-apply");
        String currentBranch = gitOutput(containerId, List.of("branch", "--show-current")).trim();
        String profileTip = "";
        String notificationTip = "";
        RepositorySnapshot.StageThreeState stageThree = RepositorySnapshot.StageThreeState.empty();
        RepositorySnapshot.StageFourState stageFour = RepositorySnapshot.StageFourState.empty();
        RepositorySnapshot.StageFiveState stageFive = RepositorySnapshot.StageFiveState.empty();
        RepositorySnapshot.TrainingState training = RepositorySnapshot.TrainingState.empty();
        if ("STAGE-GIT-02".equals(stageKey)) {
            profileTip = gitOutput(containerId, List.of("rev-parse", "refs/heads/feature/profile")).trim();
            notificationTip = gitOutput(containerId, List.of("rev-parse", "refs/heads/feature/notification")).trim();
        }
        if ("STAGE-GIT-03".equals(stageKey)) {
            String mainTip = gitOutput(containerId, List.of("rev-parse", "refs/heads/main")).trim();
            String searchTip = gitOutput(containerId, List.of("rev-parse", "refs/heads/feature/search")).trim();
            String searchParent = gitOutput(containerId, List.of("rev-parse", "refs/heads/feature/search^1")).trim();
            String searchFileBlobId = gitOutput(containerId, List.of("hash-object", "--", "search.txt")).trim();
            stageThree = new RepositorySnapshot.StageThreeState(mainTip, searchTip, searchParent, searchFileBlobId,
                    gitLines(containerId, List.of("diff", "--name-only", "--no-ext-diff", "--")),
                    gitLines(containerId, List.of("diff", "--cached", "--name-only", "--no-ext-diff", "--")),
                    unmergedPaths(containerId), gitLines(containerId, List.of("ls-files", "--others", "--exclude-standard")),
                    readStashObjectIds(containerId));
        }
        if ("STAGE-GIT-04".equals(stageKey)) {
            String mainTip = gitOutput(containerId, List.of("rev-parse", "refs/heads/main")).trim();
            String mainParent = gitOutput(containerId, List.of("rev-parse", "refs/heads/main^1")).trim();
            String featureTip = gitOutput(containerId, List.of("rev-parse", "refs/heads/feature/profile-message")).trim();
            String featureParent = gitOutput(containerId, List.of("rev-parse", "refs/heads/feature/profile-message^1")).trim();
            String mainTree = gitOutput(containerId, List.of("rev-parse", "refs/heads/main^{tree}")).trim();
            String featureTree = gitOutput(containerId, List.of("rev-parse", "refs/heads/feature/profile-message^{tree}")).trim();
            String messagesBlob = gitOutput(containerId, List.of("hash-object", "--", stageFourPath)).trim();
            stageFour = new RepositorySnapshot.StageFourState(mainTip, mainParent, featureTip, featureParent,
                    mainTree, featureTree, messagesBlob,
                    gitLines(containerId, List.of("diff", "--name-only", "--no-ext-diff", "--")),
                    gitLines(containerId, List.of("diff", "--cached", "--name-only", "--no-ext-diff", "--")),
                    unmergedPaths(containerId), gitLines(containerId, List.of("ls-files", "--others", "--exclude-standard")));
        }
        if ("STAGE-GIT-05".equals(stageKey)) {
            String mainTip = gitOutput(containerId, List.of("rev-parse", "refs/heads/main")).trim();
            String paymentRetryTip = nullableLocalBranchTip(containerId, "refs/heads/feature/payment-retry");
            List<String> localBranches = gitLines(containerId, List.of("for-each-ref", "--format=%(refname:short)", "refs/heads"))
                    .stream().sorted().toList();
            stageFive = new RepositorySnapshot.StageFiveState(mainTip, stageFiveC1, stageFiveC0, stageFiveC1Tree,
                    paymentRetryTip, localBranches);
        }
        if (stageKey.startsWith("TRAINING-GIT-")) {
            String mainTip = gitOutput(containerId, List.of("rev-parse", "refs/heads/main")).trim();
            String trainingBranchTip = nullableLocalBranchTip(containerId, "refs/heads/feature/onboarding");
            List<String> headPaths = gitLines(containerId, List.of("ls-tree", "-r", "--name-only", "HEAD"));
            List<String> workingPaths = gitLines(containerId, List.of("diff", "--name-only", "--no-ext-diff", "--"));
            List<String> indexPaths = gitLines(containerId, List.of("diff", "--cached", "--name-only", "--no-ext-diff", "--"));
            List<String> untrackedPaths = gitLines(containerId, List.of("ls-files", "--others", "--exclude-standard"));
            List<String> ignoredPaths = gitLines(containerId, List.of("ls-files", "--others", "--ignored", "--exclude-standard"));
            String introBlob = "TRAINING-GIT-01".equals(stageKey) ? fileBlob(containerId, "onboarding/intro.txt") : "";
            String ignoreBlob = "TRAINING-GIT-02".equals(stageKey) ? fileBlob(containerId, ".gitignore") : "";
            String configBlob = "TRAINING-GIT-02".equals(stageKey) ? fileBlob(containerId, "config/application-training.properties") : "";
            String reportBlob = "TRAINING-GIT-02".equals(stageKey) ? fileBlob(containerId, "build/training-report.txt") : "";
            String handoffBlob = "TRAINING-GIT-03".equals(stageKey) ? fileBlob(containerId, "docs/handoff.md") : "";
            boolean reportExists = "TRAINING-GIT-02".equals(stageKey) && pathExists(containerId, "/workspace/build/training-report.txt");
            training = new RepositorySnapshot.TrainingState(mainTip, trainingBranchTip, headPaths, workingPaths,
                    indexPaths, untrackedPaths, ignoredPaths, introBlob, ignoreBlob, configBlob, reportBlob,
                    handoffBlob, reportExists);
        }
        return new RepositorySnapshot(head, tree, firstParentTree, parentList, status.isEmpty(), reverting, ancestorList,
                currentBranch, profileTip, notificationTip, cherryPicking, merging, rebasing, stageThree, stageFour, stageFive, training);
    }

    boolean isCommit(String containerId, String objectId) {
        return "commit".equals(gitOutput(containerId, List.of("cat-file", "-t", objectId)).trim());
    }

    private String fileBlob(String containerId, String path) {
        String value = gitOutput(containerId, List.of("hash-object", "--", path)).trim();
        if (!value.matches("[0-9a-f]{40}")) throw new IllegalStateException("training fixture file is invalid");
        return value;
    }

    private boolean pathExists(String containerId, String path) {
        return docker.run(List.of("exec", containerId, "/usr/bin/test", "-f", path), timeout).exitCode() == 0;
    }

    private String nullableLocalBranchTip(String containerId, String ref) {
        var arguments = gitPrefix(containerId);
        arguments.addAll(List.of("-C", "/workspace", "show-ref", "--verify", "--quiet", ref));
        var result = docker.run(arguments, timeout);
        if (result.outputTruncated()) throw new IllegalStateException("branch snapshot failed");
        if (result.exitCode() == 1) return null;
        if (result.exitCode() != 0) throw new IllegalStateException("branch snapshot failed");
        return gitOutput(containerId, List.of("rev-parse", ref)).trim();
    }

    private String gitOutput(String containerId, List<String> gitArguments) {
        var arguments = gitPrefix(containerId);
        arguments.addAll(List.of("-C", "/workspace", "-c", "core.hooksPath=/opt/empty-hooks",
                "-c", "core.attributesFile=/dev/null", "-c", "credential.helper=", "-c", "core.pager=cat",
                "-c", "core.editor=:", "-c", "protocol.file.allow=never", "-c", "user.name=Developer Dungeon Player",
                "-c", "user.email=player@developer-dungeon.invalid"));
        arguments.addAll(gitArguments);
        var result = docker.run(arguments, timeout);
        if (result.exitCode() != 0 || result.outputTruncated()) throw new IllegalStateException("snapshot failed");
        return result.stdout();
    }

    private boolean stateFileExists(String containerId, String name) {
        var result = docker.run(List.of("exec", containerId, "/usr/bin/test", "-e", "/workspace/.git/" + name), timeout);
        if (result.outputTruncated() || (result.exitCode() != 0 && result.exitCode() != 1)) {
            throw new IllegalStateException("Git state file check failed");
        }
        return result.exitCode() == 0;
    }

    private List<String> gitLines(String containerId, List<String> gitArguments) {
        String output = gitOutput(containerId, gitArguments);
        return output.isBlank() ? List.of() : output.lines().filter(line -> !line.isBlank()).toList();
    }

    private List<String> unmergedPaths(String containerId) {
        String output = gitOutput(containerId, List.of("ls-files", "--unmerged"));
        if (output.isBlank()) return List.of();
        var paths = new LinkedHashSet<String>();
        for (String line : output.lines().toList()) {
            int separator = line.indexOf('\t');
            if (separator <= 0 || separator == line.length() - 1) throw new IllegalStateException("invalid unmerged path output");
            paths.add(line.substring(separator + 1));
        }
        return List.copyOf(paths);
    }

    private List<String> readStashObjectIds(String containerId) {
        var arguments = gitPrefix(containerId);
        arguments.addAll(List.of("-C", "/workspace", "-c", "core.hooksPath=/opt/empty-hooks", "-c", "core.attributesFile=/dev/null",
                "-c", "credential.helper=", "-c", "core.pager=cat", "-c", "core.editor=:", "-c", "protocol.file.allow=never",
                "-c", "user.name=Developer Dungeon Player", "-c", "user.email=player@developer-dungeon.invalid", "stash", "list", "--format=%H"));
        var result = docker.run(arguments, timeout);
        if (result.exitCode() != 0 || result.outputTruncated()) throw new IllegalStateException("stash snapshot failed");
        if (result.stdout().isBlank()) return List.of();
        List<String> objectIds = result.stdout().lines().toList();
        if (objectIds.stream().anyMatch(id -> !id.matches("[0-9a-f]{40}")) || new LinkedHashSet<>(objectIds).size() != objectIds.size()) {
            throw new IllegalStateException("invalid stash snapshot");
        }
        return List.copyOf(objectIds);
    }

    private ArrayList<String> gitPrefix(String containerId) {
        return new ArrayList<>(List.of("exec", "--env", "HOME=/tmp/empty-home", "--env", "XDG_CONFIG_HOME=/tmp/empty-xdg",
                "--env", "GIT_CONFIG_NOSYSTEM=1", "--env", "GIT_CONFIG_GLOBAL=/dev/null", "--env", "GIT_TERMINAL_PROMPT=0",
                "--env", "GIT_ASKPASS=/bin/false", "--env", "GIT_ALLOW_PROTOCOL=", "--env", "GIT_PROTOCOL_FROM_USER=0",
                containerId, "/usr/bin/git"));
    }
}
