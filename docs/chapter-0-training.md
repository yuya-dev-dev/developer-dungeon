# Chapter 0「Git基礎研修」仕様

## 1. 位置づけ

Chapter 0は、Gitを初めて操作する人と基礎を復習したい人へ推奨する任意の導入章である。事故対応を無理に設定せず、新人研修の通常作業としてlocal repositoryの基本を広く浅く体験する。経験者はChapter 1から開始でき、Chapter 1を始めた後もChapter 0へ戻れる。

長い講義、選択式問題、固定手順の暗記を中心にしない。実Git操作の結果として安全な最終repository状態へ到達したことだけを完了条件とし、調査commandの順序、回数、最短手順は採点しない。commitはlocalな履歴記録であり、remoteを使った共有は後続章で扱う。

## 2. 学習到達点

全3研修、25〜40分を目安とする。完了時に、command一覧とhintを参照しながら次を説明・実行できる状態を目指す。

1. working tree、index、HEADの違いを状態から確認する。
2. `status`、`diff`、`diff --staged`でcommit前の内容を確認する。
3. `add`で次のcommitへ含める変更を選び、`commit`でlocal履歴へ記録する。
4. `log --oneline`で記録済みのcommitを確認する。
5. 誤ってstageした生成物を外し、`.gitignore`の対象として残す。
6. 未commit変更を失わず固定作業branchへ移し、`main`を動かさずに記録する。

remote、merge、rebase、cherry-pick、revert、reset、stash、reflog、任意editorは扱わない。

## 3. 画面と進捗契約

- `/`のGit編cardは従来どおり`/git/stages`へ進む。
- `/git/stages`は「Git基礎研修」と「Git事故対応」を分け、`TRAINING-GIT-01`〜`03`と既存の`STAGE-GIT-01`〜`05`をそれぞれ学習順に表示する。`/git/training`は同画面の研修sectionへ戻す補助URLとする。
- 各研修は`/training/TRAINING-GIT-01`〜`03`で開始し、command、hint、resetも同じprefix配下に置く。
- Chapter 0完了をChapter 1のアクセス条件にしない。skipは失敗、辞退、評価として保存しない。
- 研修一覧と研修結果は「研修完了／未完了」を表示し、star、復旧、事故という表現を使わない。
- DB互換のため、研修clear時はhint levelとreset回数に関係なく内部starsを常に`1`として保存する。Chapter 0のUIには表示しない。
- 3研修すべてのApp route、Runner fixture、allowlist、snapshot採点が成立するまで、利用可能な入口として公開しない。

## 4. 研修仕様

### TRAINING-GIT-01「最初の変更を記録する」

- 物語: 入社初日の研修repositoryで、用意された案内文の変更を確認し、最初の研修commitを作る。
- 進行beat: 観察、commit対象の選択、記録と確認。
- 初期状態: `main`の`onboarding/intro.txt`だけが変更済みで、indexは空。
- 最終状態: 初期main tipを直接親とするcommitが作成され、期待treeと一致し、current branchは`main`、repositoryはclean。
- 成長beat: 最初の研修commitを記録し、次はcommit内容の選別も任される。

### TRAINING-GIT-02「commitに含めるものを選ぶ」

- 物語: 今回届ける成果は設定fileとignore規則であり、同じ手順で再生成できるreportは履歴へ含めない。同期の疑問を受け、プレイヤーがcommit対象を点検する。
- 初期状態: `build/training-report.txt`がstage済みで、`.gitignore`と`config/application-training.properties`は未stageの正しい変更を持つ。
- 最終状態: `.gitignore`と設定fileだけを含むcommitが作成される。生成reportはworkspaceに存在したままGitにignoreされ、HEADとindexへ含まれず、repositoryはclean。
- 成長beat: stage済みの内容も点検し、成果物だけを選び直せるようになる。

### TRAINING-GIT-03「作業branchで変更する」

- 物語: 仮配属前の模擬タスクとして、`main`上にある未commitの引継ぎ文書変更を専用branchへ移して記録する。
- 初期状態: `main`の`docs/handoff.md`だけが変更済みで、`feature/onboarding`は存在しない。
- 最終状態: `main` tipは初期値から不変。`feature/onboarding`が初期main tipを直接親とする期待commitを指し、current branchも同branch、repositoryはclean。
- 成長beat: `main`を守る作業境界を自分で作り、仮配属先へ引き継げるbranchを残す。

## 5. 固定command契約

確認commandの順序と回数は自由とする。変更commandは次のraw構文だけを受理し、専用`CommandKind`へ変換する。Runnerはraw文字列をargvへ流用せず、表の固定argvを構築する。

| 研修 | raw構文 | CommandKind | Runner固定argv |
|---|---|---|---|
| 1 | `git add onboarding/intro.txt` | `ADD_TRAINING_INTRO` | `git add -- onboarding/intro.txt` |
| 1 | `git commit -m complete-training-01` | `COMMIT_TRAINING_ONE` | `git commit -m complete-training-01` |
| 2 | `git restore --staged build/training-report.txt` | `UNSTAGE_TRAINING_REPORT` | `git restore --staged -- build/training-report.txt` |
| 2 | `git add .gitignore` | `ADD_TRAINING_IGNORE` | `git add -- .gitignore` |
| 2 | `git add config/application-training.properties` | `ADD_TRAINING_CONFIG` | `git add -- config/application-training.properties` |
| 2 | `git commit -m complete-training-02` | `COMMIT_TRAINING_TWO` | `git commit -m complete-training-02` |
| 3 | `git switch -c feature/onboarding` | `SWITCH_CREATE_TRAINING_BRANCH` | `git switch -c feature/onboarding` |
| 3 | `git switch feature/onboarding` | `SWITCH_TRAINING_BRANCH` | `git switch feature/onboarding` |
| 3 | `git add docs/handoff.md` | `ADD_TRAINING_HANDOFF` | `git add -- docs/handoff.md` |
| 3 | `git commit -m complete-training-03` | `COMMIT_TRAINING_THREE` | `git commit -m complete-training-03` |

全研修で`git status`、`git diff`、`git diff --staged`、`git log --oneline`を許可する。研修3ではこれらに`git branch`を加える。StageごとのallowlistをAppとRunnerの双方で検証し、任意path、branch、message、revision式、shell構文を許可しない。

## 6. snapshotと採点

`RepositorySnapshot`へChapter 0専用の型付きtraining stateを追加する。少なくとも初期main tip、current branch、HEADの親とtree、working tree／indexのpath、固定branch tip、生成reportの存在、ignore成立を取得する。Appはfixtureの固定object IDだけに依存せず、attempt開始時に捕捉した初期tipと、期待する構造・path状態を最終snapshotと照合する。

研修2では、生成reportが存在し、`git check-ignore`でignore対象と確認でき、HEAD treeとindexに含まれないことを同時に検証する。研修3では、main tip不変、固定branch、直接親、期待tree、current branch、clean状態を同時に検証する。

## 7. 実装境界と安全性

現行Stage基盤を明示的に拡張し、実Git、Docker隔離、attempt lifecycle、idempotency、cleanupを再利用する。汎用scenario engine、plugin、動的command定義、任意editorは導入しない。

- `V4` migrationでstage key制約を`TRAINING-GIT-01`〜`03`へ拡張する。
- command kind制約は新しい専用kindと、現行Appが保存する`EDIT_PROFILE_MESSAGES`を含む既存operation集合を正本として再定義する。
- fixtureはchallenge image内の固定read-only資材として作り、workspaceへcopy後にref、path、local config、hook、symlink、file mode、初期状態を検証する。
- player入力をhost OS、App process、shellへ渡さない。workspaceのnetwork、resource、host filesystem、Docker socket、secretへの既存制限を変更しない。
- timeout、reset、system recovery、clear後cleanup、二重送信の既存契約を変更しない。

## 8. hintと物語

Hint 1は観察場所、Hint 2は概念、Hint 3はcommand形、Hint 4は固定path・branch・messageを示す。hintとplayer resetは研修評価を下げない。具体手順は通常会話で先に教えず、repository状態との照合をプレイヤーへ残す。

研修担当は制約と観察点を示し、同期は判断理由を問いかける。主人公は「実行する」「内容を選ぶ」「mainを守る境界を作る」と責任を広げる。TRAINING 3完了後は通常の仮配属連絡を示し、Chapter 1の事故連絡はChapter 1開始後に初めて提示する。

## 9. 最小検証

- App unit: 各raw構文の受理、近似構文と危険入力の拒否、最終snapshotの正解・近似不正解、内部stars固定、Chapter別progress／route。
- Runner unit: 新`CommandKind`の固定argv、Stage別allowlist、別Stageでの拒否。
- DB integration: 新stage key、新旧operation kind、progress永続化、既存attempt lifecycle。
- Docker integration: 3 fixtureの初期状態、代表的な別解順序、最終snapshot、ignore不変条件、main tip不変、cleanup。
- 回帰: `STAGE-GIT-01`〜`05`のroute、progress、既存parser／grade／Runner allowlistを維持する。
