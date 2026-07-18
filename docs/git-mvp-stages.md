# Git編 安定版MVPステージ仕様

## 文書情報

- 状態: 既存STAGE-GIT-01〜05とPhase 5の表示・文言は実装済み。改善単位7Dの安全な複数解法対応も実装・対象限定テスト完了
- 上位文書: [`requirements.md`](requirements.md)、[`game-design.md`](game-design.md)
- 関連文書: [`threat-model.md`](threat-model.md)、[`architecture.md`](architecture.md)、[`test-strategy.md`](test-strategy.md)

## 1. この文書が決めること

この文書は、Git編安定版MVPの5ステージについて、学習目標、初期状態、許可する操作、期待する最終状態、近似不正解、ヒントを定める。

fixture内の具体的な文章、コミットメッセージ、object IDは実装時に固定する。この文書では、object IDを`C0`、`C1`のようなfixture内の論理名で表す。

## 2. 全ステージ共通仕様

### 2.1 入力

- プレイヤーは1回につき1行のGitコマンドを入力する。
- シェル構文、複数コマンド、パイプ、リダイレクト、command substitutionは受理しない。
- Gitのグローバルオプションはプレイヤーへ許可しない。
- Runnerは、ステージ別に許可されたcommand、option、ref、pathだけを構造化引数として実行する。
- `git config`、alias、外部URL、任意pathは全ステージで禁止する。
- Runnerは観察commandのobject IDを原則12桁で表示し、fixture build時に12桁prefixが許可object間で一意であることを検証する。
- appはそのattemptで実際に表示済みの12桁object ID、または完全object IDだけを受理する。
- appは短縮IDがfixture内の許可objectへ一意に対応することを確認し、完全object IDへ正規化する。
- Runnerは完全IDが現在workspace内に存在し、stageの許可objectと一致することを再検証する。
- 曖昧prefix、未知object、`HEAD~1`、`@{1}`、range、peelなどのrevision式をプレイヤー入力として受理しない。

### 2.2 採点

採点は信頼できるRunnerが取得したrepository snapshotをJavaのステージ別ポリシーが評価する。入力履歴は採点条件にしない。

安定版MVPでは、clear条件を満たしたコマンドの確定後に自動クリアし、workspaceを破棄する。ライブworkspaceを保持してプレイヤーの復旧報告を待つ状態機械は導入しない。

共通条件：

- 指定されたbranchまたはHEADが期待する位置にある。
- indexとworking treeがステージの期待状態に一致する。
- merge、rebase、cherry-pick、revertなどが意図せず途中状態で残っていない。
- ステージ固有のcommit祖先関係とtree状態を満たす。

### 2.3 ヒントとスター

全ステージで4段階ヒントと累積3スターを使う。詳細は[`game-design.md`](game-design.md)を正本とする。

全5ステージの常時表示は概念カテゴリだけとし、正確な構文はヒントレベル3、対象を含む具体手順はヒントレベル4で開示する。案内量と読み取り専用状態要約は独立した表示方針とし、command allowlist、fixture、clear policyを変更しない。

### 2.4 物語resource

各ステージに、固定導入scene、固定clear scene、主人公の成長beat、技術振り返りを持たせる。技術振り返りは「何が壊れていたか」「なぜ採用方法が安全か」「危険または状況上不適切な代替案」を含む。

クリア後は、固定解説をすぐ表示し切らず、復旧完了の根拠を考えてから解説を開く非採点・非永続の自己確認を置く。回答をスターや進捗へ使用せず、DBへ保存しない。スター別反応は任意とし、技術上のクリア条件を変えない。

## 3. STAGE-GIT-01 公開済み変更を取り消す

### シナリオ

主人公が初めて参加した現場で、公開済みの`main`に必要な設定ファイルを削除するコミットが含まれていることが判明する。運用担当は次のリリース確認を進められない。先輩は新人の主人公へ、共有履歴を書き換えずに設定を戻すよう依頼する。

### 難易度と学習目標

- 難易度: 研修
- 主概念: `revert`
- 学習目標: 公開済み履歴では、不正commitを履歴から消すのではなく、打ち消すcommitを追加する判断を理解する。

### 初期状態

- 現在branch: `main`
- `C0`: 初期commit
- `C1`: 正常な機能追加commit
- `C2`: 必要な設定ファイルを削除した公開済みcommit
- `main`は`C2`を指す。
- indexとworking treeはcleanである。

### 許可するGit操作

- `git status`
- `git log --oneline`
- `git show <fixture内で表示済みの12桁IDまたは完全ID>`
- `git revert --no-edit <C2の表示済み12桁IDまたは完全ID>`
- `git revert --no-commit <C2の表示済み12桁IDまたは完全ID>`
- `git commit -m restore-required-settings`

復旧は`revert --no-edit`で一度に確定する方法と、`revert --no-commit`で変更を確認してから固定messageでcommitする方法の両方を許可する。

### クリア条件

- 現在branchが`main`である。
- `C2`がHEADの祖先として残っている。
- HEADのtreeが`C1`のtreeと一致する。
- HEADが`C2`ではなく、revert後の新しいcommitである。
- indexとworking treeがcleanである。
- revert途中状態ではない。

### 近似不正解

- `main`を`C1`へ戻して`C2`を履歴から外した。
- ファイル内容は戻ったが未commitである。
- revertがconflictまたは途中状態で止まっている。

### ヒント

1. `main`の直近履歴と、削除されたファイルを確認する。
2. 公開済みcommitは、消すより打ち消す方法を検討する。
3. `git revert --no-edit <commit>`と`git revert --no-commit <commit>`の形を示す。
4. fixtureの`C2`に対応するobject IDと、後者を確定する固定commit commandを示す。

### クリア後の物語

- 成長beat: 主人公は、最短に見える操作ではなく、共有中の履歴を守る判断を初めて任された。
- 固定clear scene: 運用担当が設定の復旧確認を再開すると、先輩は「共有履歴を守る判断ができたね」と主人公にうなずいた。

## 4. STAGE-GIT-02 間違ったbranchのcommitを移す

### シナリオ

主人公は通知機能の変更を、誤って別の作業branchへcommitした。正しいbranchに変更がないため、QA担当はレビューを始められない。まだ共有remoteへpushされていないうちに、正しいbranchへcommitを移し、誤ったbranchを元の位置へ戻す必要がある。

### 難易度と学習目標

- 難易度: 実務
- 主概念: `cherry-pick`、未公開branchの`reset`
- 学習目標: commitを別branchへ適用し、未公開履歴だけを安全に戻す。

### 初期状態

- `C0`: 共通の基点
- `feature/profile`: `C0 -> C1`。`C1`が通知機能の変更で、誤ったbranchにある。
- `feature/notification`: `C0`を指す。
- 現在branchは`feature/profile`で、working treeはcleanである。
- `C1`は未公開であることを障害チケットに明記する。

### 許可するGit操作

- `git status`
- `git log --oneline --all --decorate`
- `git branch`
- `git show <fixture内で表示済みの12桁IDまたは完全ID>`
- `git switch <許可されたbranch>`
- `git cherry-pick <C1の表示済み12桁IDまたは完全ID>`
- `git reset --hard <C0の表示済み12桁IDまたは完全ID>`

`reset --hard`はこのステージだけに許可し、対象を`C0`へ固定する。

通知commitを先に正しいbranchへ適用する順序と、表示済みC1を保持したまま誤branchを先にC0へ戻す順序の両方を許可する。採点は順序ではなく、下記の最終branch位置とtreeを確認する。

### クリア条件

- `feature/notification`が、`C0`へ`C1`相当の変更を適用した新しいcommitを指す。
- `feature/profile`が`C0`を指す。
- 現在branchが`feature/notification`である。
- `feature/notification`のtreeが期待する通知機能のtreeと一致する。
- indexとworking treeがcleanである。
- cherry-pick途中状態ではない。

画面上の目標文は「通知機能の変更を`feature/notification`へ移し、`feature/profile`を変更前のC0へ戻す。最後に`feature/notification`をcheckoutした状態にする。」とし、2つのbranch位置と最後のcheckout状態を分けて示す。C0と対象commitのobject IDは目標文だけで開示しない。

### 近似不正解

- 正しいbranchへ変更を移したが、誤ったbranchに`C1`が残っている。
- `feature/profile`の変更をrevertし、履歴上は誤commitが残っている。
- 正しい内容がworking treeにあるだけでcommitされていない。

### ヒント

1. `--all --decorate`で2つのbranch位置を比較する。
2. 既存commitを別branchへ適用する操作と、未公開branchを戻す操作を分けて考える。
3. `switch`、`cherry-pick`、`reset --hard`の形を示し、単一の順序へ固定しない。
4. fixtureのbranch名とobject IDを含む2つの安全な順序を示す。

### クリア後の物語

- 成長beat: 主人公は、commitの内容だけでなく、branch位置の安全性をQA担当へ説明する役割を任された。
- 固定clear scene: 主人公が2つのbranchの最終位置と未共有履歴だけを戻した根拠を説明すると、QA担当は「これなら通知機能のレビューを再開できます」と受け取り、確認作業へ戻った。

## 5. STAGE-GIT-03 作業中の変更を正しいbranchへ移す

### シナリオ

同期と検索機能を分担している最中、主人公は`main`上で作業を始めてしまった。同期との共同作業を`feature/search`で続けたいが、変更はまだcommitできる段階ではない。作業を失わずに既存の`feature/search`へ移し、`main`をcleanに戻す。

### 難易度と学習目標

- 難易度: 実務
- 主概念: `stash`
- 学習目標: 未commit変更を一時退避し、別branchへ安全に持ち運ぶ。

### 初期状態

- 現在branch: `main`
- `main`は`C0`を指す。
- `feature/search`は`C0 -> C1`を指し、`C1`は対象ファイルの`W1`と重ならない行を変更している。
- `main`のworking treeに、検索機能の未commit・unstaged変更`W1`がある。
- indexはcleanである。

### 許可するGit操作

- `git status`
- `git diff`
- `git diff --staged`
- `git branch`
- `git stash push`
- `git stash list`
- `git switch feature/search`
- `git stash pop`
- `git stash apply`
- `git stash drop`

復元は`stash pop`で適用と削除を同時に行う方法と、`stash apply`後に`stash drop`する方法の両方を許可する。stash selectorは許可しない。

### クリア条件

- 現在branchが`feature/search`である。
- `W1`の期待する変更が、`C1`を基点とするworking treeへunstagedで復元されている。
- indexがcleanである。
- `main`が`C0`、`feature/search`が`C1`を指したままである。
- stash listが空である。
- merge conflictが発生していない。

このステージでは最終working treeが意図的にdirtyであるため、全ステージ共通のclean条件を適用しない。

### 近似不正解

- `main`に変更が残ったままである。
- `feature/search`へ移動したがstash内にも変更が残っている。
- 変更の一部だけが復元され、indexへ意図しない変更がある。
- 変更をcommitしてしまい、未commit作業を移す目標から外れた。

### ヒント

1. `status`と2種類の`diff`で、保存したい変更を確認する。
2. branchを切り替える前に、working treeを一時退避できる。
3. `stash push`、`switch`と、`stash pop`または`stash apply`／`stash drop`の形を示す。
4. 対象branchを含む2つの具体的な復元方法を示す。

### クリア後の物語

- 成長beat: 主人公は、作業中の変更を失わずに整理し、次の作業段取りを任されるようになった。
- 固定clear scene: 同期が作業内容が残っていることを確認し、「この後はどの順で仕上げようか」と主人公へ相談する。主人公は確認、commit、レビュー依頼までの次の段取りを示した。

## 6. STAGE-GIT-04 コンフリクトを解消して統合する

### シナリオ

主人公が応援参加する現場で、運用チームと機能チームが同じメッセージ定義を異なる目的で変更した。QA担当は片方の要件だけではリリース確認を承認できない。主人公は調整役として片方を捨てず、双方の意図を残して`main`へ統合する必要がある。

### 難易度と学習目標

- 難易度: 実務
- 主概念: merge conflict、限定ファイル編集、`add`、merge commit
- 学習目標: conflict markerを削除するだけでなく、双方の要件を満たす内容へ編集して統合する。

### 初期状態

- `C0`: 共通の基点
- `main`: `C0 -> C1`。通常時のメッセージを変更している。
- `feature/profile-message`: `C0 -> C2`。プロフィール機能用メッセージを変更している。
- 現在branchは`main`で、working treeはcleanである。
- 両branchは、指定ファイル`src/main/resources/messages.properties`の同じ箇所を変更している。

### 許可するGit操作と編集

- `git status`
- `git log --oneline --all --decorate --graph`
- `git diff`
- `git branch`
- `git merge feature/profile-message`
- `git add src/main/resources/messages.properties`
- `git commit --no-edit`
- `git commit -a --no-edit`
- 限定エディタによる`src/main/resources/messages.properties`の編集

限定エディタは`.git`、他ファイル、symlinkを読み書きできない。

限定エディタで競合内容を解消した後は、固定pathを`add`して`commit --no-edit`する方法と、追跡中の変更だけを対象に`commit -a --no-edit`する方法の両方を許可する。

### クリア条件

- 現在branchが`main`である。
- HEADが2つの直接parentを持つmerge commitで、第1parentが`C1`、第2parentが`C2`である。
- 指定ファイルが双方の要件を満たす期待内容と一致する。
- conflict markerが存在しない。
- indexとworking treeがcleanである。
- merge途中状態ではない。

### 近似不正解

- `ours`または`theirs`だけを採用し、一方の要件を失った。
- conflict markerを削除したが期待する文言が不足している。
- 内容は正しいがmerge commitを完了していない。
- 単一親の手動commitを作り、統合履歴を失った。

### ヒント

1. `status`と対象ファイルのconflict markerを確認する。
2. 正解は片方の採用ではなく、双方の要件を残す編集である。
3. 編集後に`add`し、merge commitを完了する手順を示す。
4. 期待するファイル内容と具体的なGit手順を示す。

### クリア後の物語

- 成長beat: 主人公は、コンフリクトを他者の意図を統合する作業として扱い、その判断を両チームへ説明する役割を任された。
- 固定clear scene: 主人公が競合箇所で残した双方の意図を説明すると、二つのチームが統合結果へ合意し、QA担当は検証を再開した。

## 7. STAGE-GIT-05 reflogから失われたcommitを復旧する

### シナリオ

対応終了の連絡を受けた同期が、決済APIのretry設定を実装した`feature/payment-retry`をlocalで整理した。その直後に決済障害が再発し、運用担当からretry設定の再検証を求められる。しかし通常のbranch一覧から作業は消え、`main`では別の緊急対応が進んでいるため動かせない。

先輩は主人公へ「消えたと決めつける前に、残っている証拠を見よう」とだけ伝える。主人公は元の作業が本当に失われたのかを調べ、`main`を変えずに同じ`feature/payment-retry`として取り戻し、運用担当が再検証できる状態へ戻す。

### 難易度と学習目標

- 難易度: 実務
- 主概念: `reflog`、branch復旧
- 学習目標: branch名が消えても直ちにcommit objectが失われるとは限らないこと、通常のrefをたどる`log --all`と操作履歴を記録するreflogの役割が異なることを理解する。
- 表示方針: `CONCEPT_ONLY + REDACTED_BRANCHES`。常時表示は「状態確認」「通常履歴」「操作履歴」「commit確認」「branch復旧」と、`main`が存在し復旧対象branchが存在しないことを示す最小状態要約に限定する。正確な構文はヒントレベル3以降で開示する。

### 初期状態

- `C0`: `main`の基点。決済retry設定は無効である。
- `C1`: `C0`を直接parentとし、決済retry設定を有効にした、削除前の`feature/payment-retry`の唯一のcommitである。
- fixture作成時に`main`の`C0`から`feature/payment-retry`を作成して`C1`をcommitし、`main`へ戻った後に`feature/payment-retry`を削除している。
- 現在branchは`main`、working treeはcleanである。
- local branchは`main`だけで、`main`は`C0`を指す。`git log --oneline --all --decorate`では`C1`を確認できない。
- `C1` objectは存在し、HEAD reflogの固定された8件以内に12桁短縮IDと`commit: C1: payment retry`の記録が1件だけ現れる。
- fixture作成時はcommitだけでなくbranch作成、switch、branch削除のcommitter時刻とtimezoneも固定する。reset後も同じobject ID、reflog順序、表示内容を再現する。
- Stage 5では新しいmanifest fileを導入せず、既存Stageと同じRunner側の固定fixture定義を正本にする。固定定義は`C0`、`C1`、両tree ID、初期ref集合、HEAD reflog内の期待`C1` entryを保持し、検証済みchallenge image ID／fingerprintと組み合わせて使用する。
- workspace生成時に、固定定義の`C1`がcommit objectとして存在し、`C1^1=C0`で、どのrefからも到達不能だがHEAD reflogから表示可能であることを検証する。`C0`と`C1`の`rev-parse --short=12`がそれぞれちょうど12桁で完全IDの先頭12桁と一致し、互いに異なることも検証する。13桁以上への拡張、prefix衝突、reflog行形式不一致はfixture不正としてworkspaceを公開しない。
- Runnerのinitial snapshotはStage 5専用stateとして`mainTip=C0`、`recoveryTargetId=C1`、`recoveryTargetParent=C0`、`recoveryTargetTreeId`、nullableな`paymentRetryTip`、`localBranches`を返す。refから到達不能な`C1`の完全IDはこの認証済みsnapshotだけからappへ渡し、reflog stdoutや`ancestorObjectIds`から導出しない。

### 許可するGit操作

- `git status`
- `git log --oneline --all --decorate`
- `git reflog`
- `git show <このattemptで表示済みの12桁IDまたは対応する完全ID>`
- `git branch feature/payment-retry <C1の表示済み12桁IDまたは対応する完全ID>`
- `git switch feature/payment-retry`
- `git switch -c feature/payment-retry <C1の表示済み12桁IDまたは対応する完全ID>`

入力上は上記の標準構文を使うが、Runner contractでは`git reflog`を引数なしの`REFLOG_HEAD`、branch作成を固定名と正規化済み完全`C1`だけを持つ`CREATE_PAYMENT_RETRY_BRANCH`、switchを引数なしの`SWITCH_PAYMENT_RETRY`、作成とswitchの同時実行を固定名と完全`C1`だけを持つ`SWITCH_CREATE_PAYMENT_RETRY`として扱う。Browser由来のbranch名、reflog selector、revision式、任意optionをRunnerへ渡さない。

`REFLOG_HEAD`はRunnerが固定argv `git reflog show --format=%h%x09%gs --abbrev=12 --max-count=8 HEAD`へ変換する。`--all`、`show HEAD@{n}`、`delete`、`expire`、`gc`は許可しない。reflogとlogのstdoutは採点へ使用せず、plain text escapeと既存の出力上限を適用する。

`show`とbranch作成に使えるobject IDは、Runnerの初期snapshotが返した信頼済み`C0`／`C1`のうち、そのattemptの許可済み観察commandまたはヒントレベル4で実際に12桁IDを表示したものだけとする。appが表示済み集合を管理して一意な完全IDへ正規化する。Runnerは表示履歴を保持せず、受け取った完全IDの40桁形式、commit objectの存在とtype、固定fixture allowlist、command固有targetを再検証する。branch作成は`C1`だけを許可し、`C0`、未表示ID、短すぎる・曖昧なprefix、40桁の未知ID、`HEAD@{1}`、`C1^`、`C1~1`を拒否する。

### クリア条件

- `feature/payment-retry`が`C1`を指す。
- 現在branchが`feature/payment-retry`である。
- HEADが`C1`そのもので、`C1`の直接parentが`C0`である。
- HEADのtreeが`C1`のtreeと一致する。
- `main`は初期`C0`から動いていない。
- local branch集合が`main`と`feature/payment-retry`だけである。
- indexとworking treeがcleanで、merge、rebase、cherry-pick、revertの途中状態ではない。

採点はreflog出力や入力command列をparseせず、Runnerが固定Git commandで取得する`main` tip、復旧branch tip、current branch、HEAD、HEADの直接parent、tree ID、local branch集合、porcelain状態、途中状態だけを使う。clear条件成立時は既存の自動clearとworkspace破棄を行い、復旧報告待ちは追加しない。

観察commandの順序は採点条件にしない。`status`や通常履歴を確認してからreflogへ進む経路と、reflogから対象commitを見つけて必要な内容・状態確認を前後して行う経路の少なくとも2つを許容する。branch作成前の表示済みobject ID検証、固定C1だけを許可するRunner境界、最終snapshotの全clear条件はどちらの経路でも維持する。

### 近似不正解

- `C1`の内容を新しいcommitとして作り直し、元のcommitを復旧していない。
- branchを作ったが、誤ったreflog entryを指している。
- `feature/payment-retry`を`C1`へ戻したが、`main`に留まっている。
- `main`自体を`C1`へ動かした。
- detached HEADで`C1`をcheckoutしただけで、branchを復旧していない。

### ヒント

1. 通常の`log --all`にない操作履歴を確認する。
2. branch名がなくても、以前HEADが指していたcommitを探せる。
3. `git reflog`でobject IDを探し、`git show <object>`で内容を確認してからbranchを復旧する形を示す。
4. `C1`の12桁IDを開示し、branch作成後にswitchする方法と`git switch -c feature/payment-retry <C1>`で同時に行う方法を示す。

### クリア後の振り返り

- 自己確認: 「削除されたbranchを元のcommitから復旧でき、`main`を動かしていないと判断するには、どのref、HEAD、tree、working treeを確認すべきか」。
- 安全な理由: commit内容を作り直さず元のobjectへrefを戻すため、元の履歴とobject IDを保ったまま到達可能性を回復できる。
- 危険な代替案: `main`を失われたcommitへ動かす、内容をコピーして別commitを作る、確認せず別のreflog entryへbranchを作る。
- 成長beat: 主人公は、見えているbranch一覧だけで「消失」と判断せず、操作履歴とobjectの証拠から復旧案を説明できる担当者になる。
- 固定clear scene: 主人公は、復旧したbranchが元の`C1`を指し、`main`が`C0`から動いていないことを運用担当へ説明する。運用担当がretry設定の再検証を始めると、同期は安堵する。先輩は「今回はコマンドだけでなく、根拠から復旧を説明できた。次のインシデント説明は君に任せる」と主人公へ告げる。

## 8. ステージ依存と解放順

MVPでは`STAGE-GIT-01`から順番に解放する。前ステージの1スター取得を次ステージの解放条件とする。スターの取り直しは任意とし、3スターを進行条件にしない。

## 9. fixture共通要件

- author、committer、timestamp、timezoneを固定し、object IDを再現可能にする。
- 架空の氏名、メールアドレス、ファイル、業務データだけを使用する。
- fixtureに実行可能hook、symlink、submodule、外部URL、秘密値を含めない。
- repository-local `.git/config`は、keyだけでなく期待valueも検証し、`core.repositoryformatversion=0`、`core.filemode=true`、`core.bare=false`、`core.logallrefupdates=true`を基本allowlistとする。
- alias、external diff、filter、custom merge driver、fsmonitor、credential helper、signing program、ssh commandなど、外部processまたは外部接続につながるlocal configを禁止する。
- MVP fixtureでは`.gitattributes`と`.git/info/attributes`を禁止し、`.gitmodules`も含めない。Runnerはsystem attributesを無効化する。
- stage開始時に信頼済みfixtureから新しいworkspaceを生成する。
- expected tree、重要commit、branch tipをfixture manifestに記録する。
- fixture manifestはプレイヤー入力から変更できない。

本文書の「fixture manifest」は論理的なfixture期待値集合を指す。現在のMVP実装では独立fileを新設せず、image fingerprintで保護されたfixtureとRunner側の固定定数・固定検査を使用する。appは認証済みRunnerのinitial snapshotからstage targetを取得し、player向けstdoutを期待値の正本にしない。

## 10. MVP後の候補

- interactive rebaseによる履歴整理
- bisectによる不具合混入commitの特定
- ローカルに閉じた疑似remoteの食い違い
- 複数のrevertまたはrevertのrevert
- stash conflict
- detached HEADからの復旧

これらは安定版MVPの完成と学習効果検証後に、個別ステージとして再評価する。
