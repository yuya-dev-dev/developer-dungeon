# Developer Dungeon 開発ロードマップ

## 文書情報

- 状態: Phase 1完了、Phase 2実装前レビューPASS
- 現在地: Phase 2の工程1〜6完了。ユーザーの実装開始承認待ち
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
| 2 | Git Runner hardening | 次工程・着手判断待ち |
| 3 | 安定版MVP基盤 | 未着手 |
| 4 | 5ステージ完成 | 未着手 |
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

1. STAGE-GIT-02 cherry-pick
2. STAGE-GIT-03 stash
3. STAGE-GIT-04 merge conflict
4. STAGE-GIT-05 reflog
5. シーズン1の物語接続と振り返り

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

1. Phase 2の対象を、Phase 1で未確認のsecurity受け入れ条件と異常系へ限定した。
2. threat modelとtest strategyから、実containerで確認すべき項目の対応表を作成した。
3. timeout、reset、異常終了、TTL、orphan回収のDocker integration test方針を確定した。
4. network、mount、user、capability、resource limit、Docker socket非公開の自動検証方針を確定した。
5. launcherの失敗経路とWindows環境差を、Docker不要のcontract testへ追加する方針を確定した。
6. 井上の実装前レビューでP1・P2をすべて解消し、PASSを得た。

詳細は[`docs/phase-2-hardening-plan.md`](docs/phase-2-hardening-plan.md)を正本とする。Phase 2では新しいstage、PostgreSQL、認証、外部公開、世界観の大幅拡張を同時に行わない。これらはRunnerの安全境界を確認した後の各Phaseで扱う。

次はユーザーの明示指示後に、Phase 2実装用branchをメインエージェントが作成して着手する。実装前レビューPASSは実装開始の自動承認を意味しない。
