# Phase 5 改善単位7D 別解対応計画

## 文書情報

- 状態: 実装・実装後レビュー・対象限定テスト完了
- レビュー: 井上の実装前レビュー、実装後再レビューともに`PASS`（2026-07-18）
- 検証: App／Runnerの対象unit test、Runner Docker integration test 7件、DB integration test 2件が成功

## 1. 目的

改善単位7Dでは、固定コマンド列を再現するのではなく、各Stageが定める安全な最終repository状態へ到達したことをクリア条件とする既存方針を、実際のプレイ可能な経路へ反映する。

コマンド数、観察順序、最短手順は採点しない。各コマンド後に信頼済みsnapshotを評価する現在の自動clearは維持するが、評価対象は「直前のコマンドが正解か」ではなく「Stage固有の最終不変条件へ到達したか」とする。途中状態は失敗扱いせず、通常のGitエラーまたは入力拒否でない限り「目標状態へ未到達」として同じworkspaceを継続する。

## 2. 対象と対象外

### 対象

- `STAGE-GIT-01`〜`STAGE-GIT-05`の安全な第2経路
- Appのstage別parser、object ID正規化、hint
- Runner contractの型付きcommand
- Runnerのstage別allowlist、固定引数、対象検証
- `command_history.command_kind`制約を新しい型付きcommandへ追従させる追加migration
- App unit test、Runner unit test、対象StageのDocker integration test、migration integration test

### 対象外

- 任意のGit subcommand、任意引数、shell、revision式の許可
- command履歴、コマンド数、実行順序による採点
- fixtureのランダム化、汎用stage engine、汎用command parser
- report操作、clear候補待機、workspace TTL延長
- 既存のattempt lifecycle、cleanup、認証、Docker分離境界の変更
- Stageの物語、登場人物、学習目標の変更

## 3. 共通の技術方針

1. `StageRules.grade`は、信頼済みsnapshotと初期fixtureから捕捉したtargetだけを評価する。command kind、command履歴、sequence numberを渡さない。
2. Appは完全一致する構文だけを型付き`GitCommand`へ変換する。object IDを取るcommandは、現在と同じ表示済み12桁IDから一意な固定40桁IDへ正規化する。
3. Runnerは新commandについてもAppを信頼せず、command shape、Stage、固定branch・固定path・固定object targetを再検証する。
4. Dockerへ渡す引数は`ProcessBuilder`用の固定配列として構築し、player入力をshell文字列へ連結しない。
5. 代替経路の途中でGitエラーが発生してもworkspaceを自動rollbackしない。結果不明のRunner障害だけは既存のsystem recoveryへ従う。
6. 最終不変条件は緩和しない。履歴構造が学習目標に含まれるStageでは、同じtreeだけでなく必要な親、branch tip、current branch、clean状態も維持する。
7. 新しい`CommandKind`はDBの許可値へ追加する。既存値と履歴は変更せず、migrationはcheck constraintの置換だけに限定する。

## 4. Stage別の最終不変条件と許容経路

### Stage 1: 公開済み変更を取り消す

最終不変条件は、誤commitが祖先として残り、HEADがその誤commitを直接親に持つ新commitであり、treeが事故前の安全なtreeへ戻り、作業ツリーがcleanで進行中操作がないこととする。

- 経路A: `git revert --no-edit <target>`
- 経路B: `git revert --no-commit <target>`の後、固定messageの`git commit -m restore-required-settings`

追加commandは`REVERT_NO_COMMIT`と`COMMIT_RESTORE_SETTINGS`とする。revert対象は既存と同じ表示済み固定誤commitだけを許可し、commit messageをplayer任意入力にしない。

### Stage 2: 間違ったbranchのcommitを移す

最終不変条件は、`feature/profile`がC0、`feature/notification`がC0を親に持ち通知機能treeを含むcommit、current branchが`feature/notification`、作業ツリーがcleanで進行中操作がないこととする。

- 経路A: 通知commitを`feature/notification`へcherry-pickしてから`feature/profile`をC0へ戻す
- 経路B: C1を履歴で確認してから`feature/profile`をC0へ戻し、`feature/notification`へ移ってC1をcherry-pickする

既存の型付きcommandだけで両経路を構成できるため、新commandは追加しない。C0/C1を表示済みobjectとして確認する既存境界を維持する。

### Stage 3: 未commit作業を正しいbranchへ移す

最終不変条件は、mainと`feature/search`のtipが不変、current branchが`feature/search`、対象変更が未commitのworking treeにだけ存在し、index・unmerged・untracked・stashが空であることとする。

- 経路A: `git stash pop`で適用とstash削除を同時に行う
- 経路B: `git stash apply`で適用した後、`git stash drop`でstashを削除する

追加commandは引数なしの`STASH_APPLY`と`STASH_DROP`とする。stash selectorや任意revisionは許可しない。

### Stage 4: コンフリクトを解消して統合する

最終不変条件は、mainのHEADが初期main tipと固定feature tipをこの順で直接親に持つmerge commitであり、messages fileが双方の要件を含む固定内容、作業ツリー・index・unmerged・untrackedが空で進行中操作がないこととする。

- 経路A: 限定editorで解消し、固定pathを`git add`した後に`git commit --no-edit`
- 経路B: 限定editorで解消し、`git commit -a --no-edit`で固定fixture内の追跡対象変更をstageしてmerge commitを確定する

追加commandは引数なしの`COMMIT_ALL_NO_EDIT`とし、Stage 4だけで許可する。Docker integration testで、未解消markerや想定外pathがある状態をclearしないことを確認する。

### Stage 5: 消えたcommitを復旧する

最終不変条件は、`feature/payment-retry`が固定C1、mainが固定C0、current branchとHEADがC1、treeが復旧対象tree、local branch集合が固定2branch、作業ツリーがcleanで進行中操作がないこととする。

- 経路A: 固定branchをC1に作成してからswitchする
- 経路B: `git switch -c feature/payment-retry <C1>`で作成とswitchを同時に行う

追加commandは`SWITCH_CREATE_PAYMENT_RETRY`とする。C1はreflogの成功かhint 4で表示済みになった固定40桁IDだけを許可し、branch名も固定する。

## 5. Hint方針

- Hint 1と2は証拠と守るべき不変条件を示す。
- Hint 3は使用可能な操作の形を示すが、単一の順序へ固定しない。
- Hint 4はtargetを開示し、複数経路があるStageではどちらも安全な候補として示す。
- 途中状態の通常feedbackは「目標状態へ未到達」とし、「前の手へ戻る」「正解手順から外れた」と表現しない。

## 6. 変更予定ファイル

- `runner-contract/.../CommandKind.java`
- `git-runner/.../RunnerCommandValidator.java`
- `git-runner/.../RunnerWorkspaceService.java`
- `app/.../StageRules.java`
- `app/.../StageController.java`（command参照表へ新commandを追記する場合だけ）
- `db-migrator/.../V3__expand_alternative_solution_command_kinds.sql`
- 上記に直接対応するApp、Runner、DBの対象限定test
- 本文書、`phase-5-experience-improvement-plan.md`、`git-mvp-stages.md`、`roadmap.md`

## 7. テスト方針

1. App unit testで、各新構文の型変換、Stage外拒否、object ID表示済み境界、各代替経路の最終snapshot clearを確認する。
2. Runner unit testで、新commandの引数なし／40桁object ID形状と不正target拒否を確認する。
3. Runner Docker integration testでStage 1〜5の第2経路を各1本だけ実行し、最終不変条件を確認する。
4. DB integration testで全`CommandKind`を保存でき、未知kindを拒否することを確認する。
5. 既存の標準経路testは削除せず、別解追加による回帰がないことを対象限定で確認する。

## 8. 完成条件

- 全5Stageで標準経路と第2経路の双方が、同じStage固有の最終不変条件によりclearする。
- command数、観察順序、経路名が採点へ入っていない。
- 各新commandがStage、引数、branch、path、object IDの固定境界をAppとRunnerの両方で検証される。
- 既存の危険入力拒否、attempt継続、cleanup、自動clearを変更していない。
- 井上の実装前レビューでP1がなく、実装後レビューで次工程を止める指摘がない。
- 対象限定testと最終差分チェックが成功している。
