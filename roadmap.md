# Developer Dungeon 開発ロードマップ

## 文書情報

- 状態: Phase 4進行中、STAGE-GIT-01／02とゲームループ補完完了、STAGE-GIT-03実装完了・PR前
- 現在地: STAGE-GIT-03（stash）の固定fixture、snapshot採点、CONCEPT_ONLY＋状態要約OFF、対象限定テストまで完了。次はPR後にSTAGE-GIT-04を個別設計する
- 上位文書: [`docs/requirements.md`](docs/requirements.md)
- 関連文書: [`docs/vertical-slice.md`](docs/vertical-slice.md)、[`docs/test-strategy.md`](docs/test-strategy.md)、[`docs/phase-2-hardening-plan.md`](docs/phase-2-hardening-plan.md)

## 1. ロードマップの役割

この文書は開発段階、完成条件、次段階へ進む条件を管理する。プロダクト要件や設計詳細を新たに決めず、正本となる`docs`配下の文書を参照する。

## 2. 現在の方針

- 現在のリポジトリはGit編専用である。
- 安全な1日縦切り版の実装と再評価結果を基準に、Runnerを段階的にhardeningする。
- 安定版MVPは5ステージとする。
- Java、SQL、Docker学習編はMVP完成後に別途再評価する。
- コード変更前の作業ブランチ作成・切替はメインエージェントが行う。
- `git add`、commit、push、PR、mergeはユーザーが行う。

## 3. フェーズ一覧

| Phase | 内容 | 状態 |
|---|---|---|
| 0 | 企画・要件定義・全体設計 | 完了・ユーザー承認済み |
| 1 | 安全な1日縦切り版 | 完了・PR #1マージ済み |
| 2 | Git Runner hardening | 完了・main反映済み |
| 3 | 安定版MVP基盤 | 完了・main反映済み |
| 4 | 5ステージ完成 | 進行中・STAGE-GIT-01／02完了 |
| 5 | MVP検証と改善 | 未着手 |
| 6 | 高難度Gitステージ | 未着手 |
| 7 | 将来編の再評価 | 未着手 |

## 4. Phase 0 企画・要件定義・全体設計

### 作成物

- `docs/requirements.md`
- `docs/game-design.md`
- `docs/git-mvp-stages.md`
- `docs/threat-model.md`
- `docs/architecture.md`
- `docs/vertical-slice.md`
- `docs/test-strategy.md`
- `roadmap.md`
- `README.md`

### 完成条件

1. 9文書が依存順に作成されている。
2. 用語、要件ID、stage ID、security ID、相互linkが整合している。
3. 現在範囲と将来構想が分離されている。
4. 井上が文書一式をまとめてレビューしている。
5. 井上のP1がすべて修正されている。
6. 未確定事項が明示されている。

### 次へ進む条件

- ユーザーが文書一式を承認している。
- 初期対応環境をWindows 11 x86_64＋Docker Desktop WSL 2 backend＋Linux containerに限定している。
- 実装基準versionとRunner起動・認証方式を確定し、井上の実装前レビューを通過している。

実装開始はPhase 0の承認とは別に、ユーザーの明示指示を必要とする。

## 5. Phase 1 安全な1日縦切り版

### 内容

- STAGE-GIT-01
- Spring Boot / Thymeleaf 1画面
- appとGit Runnerの別process
- PowerShell launcher、起動時token、loopback限定API、固定image ID preflight
- disposable challenge container
- command allowlist
- state-based grading
- hint、3スター、reset
- 最小の物語導入と振り返り

### 完成条件

[`docs/vertical-slice.md`](docs/vertical-slice.md)の完成条件をすべて満たす。

### 次へ進む条件

- host実行へfallbackせず、実Gitのend-to-end体験を確認できる。
- 物語量、操作感、採点の理解、安全境界を再評価している。
- 発見した要件変更を上流文書へ反映している。

### 実績と再評価結果

- Java 25／Spring Boot／Thymeleaf、AppとGit Runnerの別process、Docker challenge containerで縦切り構成が成立した。
- `status`、`log`、`show`、`revert`を使う観察・判断・復旧loopと、repository stateによる採点をブラウザから完走できた。
- shell構文を含む禁止入力がRunnerへ送られず、clear後にchallenge containerが残らないことを確認した。
- 固定version preflight、Windows子process環境、Docker inspect、Git引数正規化など、実環境でのみ判明する不整合を修正した。
- 最小の物語導入でも課題目的は理解できる一方、世界観、演出、画面の手触りは安定版MVPへ向けた改善余地がある。
- hintと3スターは動作した。学習効果と難易度幅の評価は、5stage完成後のPhase 5で対象ユーザーにより検証する。

Phase 1の次へ進む条件は満たした。安全境界を5stageへ拡張する前にPhase 2を実施する。

## 6. Phase 2 Git Runner hardening

### 内容

- command policyのstage別拡張
- Git config、hook、protocolの固定
- repository-local configとattributesのallowlist検査
- CPU、memory、PID、workspace、output、timeout制限
- orphan cleanup
- Phase 1で導入したRunner token、loopback、launcherのhardeningと追加異常系回帰test
- Git出力とeditor内容のplain-text escape、CSP
- attempt単位の直列化、request ID、workspace generation、idempotency
- malicious inputとcontainer設定のintegration test
- STAGE-GIT-04用限定editorの安全な境界

### 完成条件

- [`docs/threat-model.md`](docs/threat-model.md)のセキュリティ受け入れ条件を実containerで確認している。
- timeout、reset、異常終了でcontainerが残らない。
- appとchallenge containerにDocker socketとhost bind mountがない。

### 次へ進む条件

- 5stageへ必要なcommandとeditorを追加できる安全な境界がある。

## 7. Phase 3 安定版MVP基盤

### 内容

- PostgreSQL
- Flyway
- Spring JDBC
- `stage_attempt`、`command_history`
- ステージ一覧
- progressと最高スター
- Docker Composeによる管理DB起動
- persistence integration test

### 完成条件

- app再起動後もclear progressが残る。
- attemptとhistoryのtransactionとconstraintがテストされている。
- Runnerとchallenge containerが管理DBへ到達できない。

### 次へ進む条件

- STAGE-GIT-02〜05を追加しても、attempt、reset、historyを共通use caseで扱える。

## 8. Phase 4 5ステージ完成

### 実装順

1. STAGE-GIT-02 cherry-pick（完了）
2. Stage 1・2のゲームループ補完（完了）
3. STAGE-GIT-03 stash（実装完了・PR前。案内量削減を先行確認し、読み取り専用状態要約はOFF）
4. STAGE-GIT-04 merge conflict
5. STAGE-GIT-05 reflog
6. シーズン1全体の物語接続と振り返り整合

### 完成条件

- [`docs/requirements.md`](docs/requirements.md)のMVP完成条件をすべて満たす。
- [`docs/test-strategy.md`](docs/test-strategy.md)のMVPテスト完了条件を満たす。
- 5stageを手動でclearできる。

### 次へ進む条件

- 新規機能追加を止め、対象ユーザーによる検証へ移れる。

## 9. Phase 5 MVP検証と改善

### 検証すること

- 状態確認から始められるか。
- exact hintなしで問題を解けるか。
- 未知の類似fixtureへ判断を転用できるか。
- revertとresetなどの使い分けを説明できるか。
- 物語が薄すぎる、長すぎる、正解誘導になっていないか。
- Runner待ち時間とresetが離脱原因にならないか。
- クリア後の自己確認だけで復旧根拠を考える体験が成立するか、ライブworkspace上の復旧報告が必要か。
- player resetを3スター条件に含めることが、安全な試行錯誤を妨げていないか。

### 完成条件

- 実装前に決めた対象人数と成功閾値で検証結果を記録している。
- 学習効果またはゲーム体験の重大な欠陥を要件へ反映している。

### 次へ進む条件

- Git編を継続改善する価値が確認できる。

## 10. Phase 6 高難度Gitステージ

候補：

- interactive rebase
- bisect
- ローカル疑似remote
- stash conflict
- detached HEAD
- 複数事故の複合stage

安定版MVPの検証結果をもとに、1stageずつ個別承認する。ランダム生成や汎用stage engineは導入しない。

## 11. Phase 7 将来編の再評価

### Javaコードレビュー編

- 同じ主人公と世界観が学習体験に有効かを検討する。
- Git編のRunnerやstage実装を無理に共通化しない。

### SQL編

- 管理DBと別instance、別network、別credentialを前提に脅威モデルを作り直す。
- Git編との統合、同一repository内module、別applicationを再比較する。

### Docker障害対応編

- Docker操作自体がhost支配につながるため、専用VM級の隔離costを再評価する。

いずれもGit編MVPの完成と検証前には着手しない。

## 12. 現在の次作業

1. STAGE-GIT-01／02、ゲームループ補完、MVP永続化、固定ルート、状態採点、対象限定テストは完了している。
2. STAGE-GIT-03では、`main`上の未commit変更を`feature/search`へstashで移す。最終working treeは意図的にdirty、index／stash／途中状態は空、branch tipは不変というsnapshotで自動clearする。
3. 表示は`CONCEPT_ONLY`＋`incidentBoardMode=OFF`で開始し、正確な構文はhint level 3以降だけに表示する。状態要約の有効化はPhase 5の観察結果を待つ。
4. ライブworkspaceの完了を宣言する復旧報告、`POST /report`、report待機用状態機械、TTL、sweeperは採用せず、Phase 5で必要性を再評価する。
5. このPRの後、STAGE-GIT-04を個別設計し、井上の実装前レビューを通してから実装する。

詳細は[`docs/requirements.md`](docs/requirements.md)、[`docs/game-design.md`](docs/game-design.md)、[`docs/git-mvp-stages.md`](docs/git-mvp-stages.md)、[`docs/architecture.md`](docs/architecture.md)、[`docs/test-strategy.md`](docs/test-strategy.md)を正本とする。

コード変更では作業branch作成、実装前後レビュー、対象限定テストを行う。commit、push、PR作成、mergeはユーザーが行う。
