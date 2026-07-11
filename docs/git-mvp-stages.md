# Git編 安定版MVPステージ仕様

## 文書情報

- 状態: 承認済み（井上の実装前レビューP1解消済み、実装指示待ち）
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

共通条件：

- 指定されたbranchまたはHEADが期待する位置にある。
- indexとworking treeがステージの期待状態に一致する。
- merge、rebase、cherry-pick、revertなどが意図せず途中状態で残っていない。
- ステージ固有のcommit祖先関係とtree状態を満たす。

### 2.3 ヒントとスター

全ステージで4段階ヒントと累積3スターを使う。詳細は[`game-design.md`](game-design.md)を正本とする。

### 2.4 物語resource

各ステージに、固定導入scene、固定clear scene、主人公の成長beat、技術振り返りを持たせる。スター別反応は任意とし、技術上のクリア条件を変えない。

## 3. STAGE-GIT-01 公開済み変更を取り消す

### シナリオ

主人公が初めて参加した現場で、公開済みの`main`に必要な設定ファイルを削除するコミットが含まれていることが判明する。運用担当から、共有履歴を書き換えずに設定を戻すよう依頼される。

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
3. `git revert --no-edit <commit>`の形を示す。
4. fixtureの`C2`に対応するobject IDを含む具体的な入力を示す。

## 4. STAGE-GIT-02 間違ったbranchのcommitを移す

### シナリオ

主人公は通知機能の変更を、誤って別の作業branchへcommitした。まだ共有remoteへpushされていないため、正しいbranchへcommitを移し、誤ったbranchを元の位置へ戻す必要がある。

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

### クリア条件

- `feature/notification`が、`C0`へ`C1`相当の変更を適用した新しいcommitを指す。
- `feature/profile`が`C0`を指す。
- 現在branchが`feature/notification`である。
- `feature/notification`のtreeが期待する通知機能のtreeと一致する。
- indexとworking treeがcleanである。
- cherry-pick途中状態ではない。

### 近似不正解

- 正しいbranchへ変更を移したが、誤ったbranchに`C1`が残っている。
- `feature/profile`の変更をrevertし、履歴上は誤commitが残っている。
- 正しい内容がworking treeにあるだけでcommitされていない。

### ヒント

1. `--all --decorate`で2つのbranch位置を比較する。
2. 既存commitを別branchへ適用する操作と、未公開branchを戻す操作を分けて考える。
3. `switch`、`cherry-pick`、`reset --hard`の順序例をプレースホルダー付きで示す。
4. fixtureのbranch名とobject IDを含む具体的な手順を示す。

## 5. STAGE-GIT-03 作業中の変更を正しいbranchへ移す

### シナリオ

主人公は`main`上で検索機能の作業を始めてしまった。変更はまだcommitしていない。作業を失わずに既存の`feature/search`へ移し、`main`をcleanに戻す。

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
3. `stash push`、`switch`、`stash pop`の形を示す。
4. 対象branchを含む具体的な手順を示す。

## 6. STAGE-GIT-04 コンフリクトを解消して統合する

### シナリオ

主人公のチームと別チームが、同じメッセージ定義を異なる目的で変更した。片方を捨てるのではなく、双方の意図を残して`main`へ統合する必要がある。

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
- 限定エディタによる`src/main/resources/messages.properties`の編集

限定エディタは`.git`、他ファイル、symlinkを読み書きできない。

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

## 7. STAGE-GIT-05 reflogから失われたcommitを復旧する

### シナリオ

作業branchを削除した直後、まだ必要な変更が含まれていたことが判明する。主人公はGitが保持する操作履歴からcommitを特定し、branchを復旧する。

### 難易度と学習目標

- 難易度: 実務
- 主概念: `reflog`、branch復旧
- 学習目標: branch名が消えても、直ちにcommit objectが失われるとは限らないことを理解する。

### 初期状態

- `C0`: `main`の基点
- `C1`: 削除された`feature/payment-retry`に存在していたcommit
- fixture作成時に`feature/payment-retry`へ移動して`C1`を作成し、`main`へ戻った後にbranchを削除している。
- 現在branchは`main`、working treeはcleanである。
- HEAD reflogから`C1`を特定できる。

### 許可するGit操作

- `git status`
- `git log --oneline --all --decorate`
- `git reflog`
- `git show <fixture内で表示済みの12桁IDまたは完全ID>`
- `git branch feature/payment-retry <C1の表示済み12桁IDまたは完全ID>`
- `git switch feature/payment-retry`

### クリア条件

- `feature/payment-retry`が`C1`を指す。
- 現在branchが`feature/payment-retry`である。
- HEADのtreeが`C1`のtreeと一致する。
- indexとworking treeがcleanである。

### 近似不正解

- `C1`の内容を新しいcommitとして作り直し、元のcommitを復旧していない。
- branchを作ったが、誤ったreflog entryを指している。
- detached HEADで`C1`をcheckoutしただけで、branchを復旧していない。

### ヒント

1. 通常の`log --all`にない操作履歴を確認する。
2. branch名がなくても、以前HEADが指していたcommitを探せる。
3. `reflog`でobject IDを探し、`branch <name> <object>`を使う形を示す。
4. fixtureのreflog entryと具体的な復旧手順を示す。

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

## 10. MVP後の候補

- interactive rebaseによる履歴整理
- bisectによる不具合混入commitの特定
- ローカルに閉じた疑似remoteの食い違い
- 複数のrevertまたはrevertのrevert
- stash conflict
- detached HEADからの復旧

これらは安定版MVPの完成と学習効果検証後に、個別ステージとして再評価する。
