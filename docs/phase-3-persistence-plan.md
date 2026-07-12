# Phase 3 安定版MVP基盤 実装計画

## 1. 目的と今回の完成条件

Phase 2で確立したGit Runner境界を維持したまま、STAGE-GIT-01のattemptとcommand historyを管理用PostgreSQLへ永続化する。今回の完成条件は、空DBへFlyway migrationを適用でき、開始・コマンド結果・ヒント・reset・clearが保存され、app再起動後もclear済みの最高スターを導出できることである。

初期対応環境はWindows 11、Docker Desktop、正式な起動入口は`scripts/start-local.ps1`のままとする。

## 2. 今回実装する範囲

- PostgreSQL 18.4の管理DB用Compose service
- 一回限りの専用migrator JVMで実行するFlyway、app用Spring JDBC、PostgreSQL JDBC driver
- `stage_attempt`、`command_history`のV1 migration
- attempt開始、hint、commandのPENDING／終端結果、reset、system recovery、clearのrepository操作
- STAGE-GIT-01 serviceからrepositoryへの最小接続
- CLEARED attemptから最高スターを導出するquery
- PostgreSQL Testcontainersを使う対象限定persistence integration test 1 class
- launcherによるDB readiness、appへのcredential受渡し、逆順停止

## 3. 実装しない範囲

- login、player、player_progress、複数ユーザー識別
- STAGE-GIT-02〜05、ステージ一覧画面の完成
- JPA、汎用repository framework、将来編向け共通schema
- cloud DB、外部公開、本番credential
- Browser E2Eと全テスト実行

## 4. 匿名利用と進捗の境界

ローカルMVPは単一プレイヤーとし、session、cookie、fingerprintで恒久player IDを作らない。`player`列と`player`tableも追加しない。進捗と最高スターは、このローカル管理DBにある同一`stage_key`の`CLEARED` attempt全体から導出する。

この方式は共有端末のユーザー別進捗を区別しない。複数ユーザー対応は外部公開またはlogin採用時に別途要件化する。

## 5. DBとcredential境界

- management PostgreSQLだけをComposeで管理し、Git challenge containerはCompose networkへ接続しない。
- DB portは`127.0.0.1`だけへbindし、appだけが接続情報を受け取る。
- Runner processとRunnerが起動するDocker CLI／challenge containerへDB URL、user、passwordを渡さない。
- bootstrap管理者、migration role、app roleを分離する。初期volumeではPostgreSQL公式entrypointのinit scriptが秘密fileを読み、固定名の2 roleとschemaを作る。migration roleだけがschema変更権限を持ち、app roleは対象tableの必要なDMLとsequence利用だけを持つ。
- Flywayは専用`db-migrator` JVMで実行し、migration完了後にprocessを終了する。app processへmigration／bootstrap credentialを渡さず、appにはruntime credentialだけを渡す。
- passwordはrepositoryへ保存せず、`.developer-dungeon/runtime`配下のgit管理外fileへ初回だけ生成し、Windows ACLを現在ユーザーとSYSTEMだけに限定する。launcherが値をlogへ出さず、Compose secret、migrator専用環境、app専用環境へ必要な値だけを渡す。
- 固定volume `developer-dungeon-postgres-data`が存在するのにcredential fileが欠落・破損している場合は再生成せずfail closedにする。初回生成はvolumeが存在しない場合だけ許可する。
- Compose file、project名`developer-dungeon`、service名`postgres`、固定volume名を固定する。起動・停止前にCompose labelを検証し、別project／serviceを操作しない。
- Compose停止時にvolumeを削除しない。`down -v`、全volume削除、pruneは使用しない。

## 6. lifecycle

1. launcherが既存preflightを完了する。
2. credential fileを検証または初回生成する。
3. pin済みPostgreSQL imageからmanagement DBだけを起動し、healthcheck成功を待つ。
4. 専用migrator JVMへmigration credentialだけを渡してFlywayを実行し、正常終了とschema versionを確認する。
5. Runnerを起動し、その後runtime credentialだけを持つappを起動する。
6. 終了時はapp、Runner、PostgreSQLの順で停止する。
7. DB停止失敗を正常終了として隠さず、volumeは保持して次回回復可能にする。

## 7. 状態遷移と永続化順序

- 新attemptは`STARTING`として、対象generationと安定したcreate request IDをDBへ先に保存する。Runner create成功後にworkspace IDを保存して`ACTIVE`へ確定する。確定DB更新に失敗した場合は同じrequest IDの結果からworkspaceを特定して削除し、未管理workspaceを残さない。
- commandはattemptを`EXECUTING`へ遷移させ、Runner呼出し前に`PENDING`を一意な`request_id`で保存する。結果を`REJECTED`、`GIT_ERROR`、`SUCCEEDED`、`TIMEOUT`、`RUNNER_ERROR`のいずれかへ終端化して`ACTIVE`へ戻す。Runner通信中にDB transactionを保持しない。
- 同一`request_id`が既に存在する場合はGitを再実行しない。
- hintは`highest_hint_level`を単調増加させる。
- resetは`RESETTING`へ遷移し、旧workspace ID、対象generation、安定したcleanup request IDと次create request IDを先に保存する。同じcleanup IDによる削除成功後だけgenerationとplayer reset countを増加し、`RESET` historyを保存して次workspaceを作る。削除失敗は`CLEANUP_PENDING`とし、新workspaceを作らない。
- system recoveryも同じ遷移を使い、system recovery countだけを増加する。
- clearは採点後に`CLEARING`へ遷移し、予定starsと安定したcleanup request IDを保存する。同じIDによるcontainer削除成功後に`CLEARED`、stars、completed timestampを確定する。DB更新失敗時にGit commandを自動再実行しない。
- startup recoveryは`STARTING`、`ACTIVE`、`EXECUTING`、`CLEARING`、`RESETTING`、`CLEANUP_PENDING`を明示的に扱う。`EXECUTING`のPENDING historyは`RUNNER_ERROR`へ終端化する。workspace IDがある状態は安定cleanup IDで削除確認後にだけ次generationを作る。`CLEARING`は同じcleanup IDを再送して`CLEARED`を確定する。矛盾、複数非終端attempt、未知状態ではworkspaceを新規作成せずfail closedにする。
- 外部操作を伴う全遷移は「短いDB transactionで意図を保存→transaction外でRunner操作→短いDB transactionで結果確定」とし、各更新をversion付きoptimistic lockで行う。

## 8. schema制約

- statusは`STARTING / ACTIVE / EXECUTING / CLEARING / RESETTING / CLEANUP_PENDING / CLEARED / FAILED / EXPIRED / ABANDONED`に限定する。UUID、stage key、result kindもDB側で制約する。
- count、generation、sequence、durationは0以上、hintは0〜4、starsはCLEARED時だけ1〜3とする。
- `completed_at`と`stars`のnull条件をstatusと整合させる。
- `(attempt_id, sequence_no)`と`request_id`をuniqueにする。
- 同一`stage_key`で終端状態以外のattemptを最大1件にするpartial unique indexを作る。
- `workspace_id`、`create_request_id`、`cleanup_request_id`、`pending_stars`を状態に応じて必須またはnullにするcheck constraintを設ける。
- command historyはattempt削除でcascade削除しない。通常操作にattempt削除を提供しない。
- optimistic updateは`id`と`version`を条件にし、更新件数1件以外を競合として拒否する。
- accepted commandの`entered_text`はparser成功後のcanonical表現だけを最大256文字で保存する。REJECTEDではraw入力を保存せず、`entered_text`と`command_kind`をnull、固定allowlistの`reason_code`だけを保存する。改行、制御文字、credential markerをhistory、例外、logへ残さない。

## 9. 必要最低限のテスト

メインと中谷は変更を証明する次の範囲だけを実行する。

1. Docker不要: 既存`StageOneServiceTest`の変更対象method
2. PostgreSQLあり: 新規persistence integration test 1 class
   - migration適用
   - migration再適用
   - constraint／unique key
   - 同一stageの非終端attempt競合
   - PENDINGから終端結果
   - optimistic lock
   - reset countとsystem recovery countの分離
   - clearと最高スター導出
   - `STARTING / ACTIVE / EXECUTING / CLEARING / RESETTING / CLEANUP_PENDING`のstartup recovery判断
   - clear／resetの確定DB更新失敗時に中間状態と安定request IDが保持されること
   - REJECTED raw入力が保存されないこと
   - app roleのDDL拒否

Runner、全Web、全module、Browser E2Eは今回実行しない。PostgreSQL integration testはユーザーがPhase 3工程6までを指示した今回に限り実行対象とする。

## 10. Gitとレビュー

メインが`codex/phase-3-persistence-foundation`を作成する。実装後はメインの差分・コンパイル・必要最低限確認、井上の実装後レビューとP1/P2修正、中谷の上記2対象だけのテスト、最終差分確認まで行う。commit、push、PR、mergeはユーザーが行う。
