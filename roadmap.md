# Developer Dungeon 開発ロードマップ

## 文書情報

- 状態: Phase 4完了、Phase 5内部パイロット・改善設計レビュー完了、改善単位1〜6はmain反映済み・最終検証待ち
- 現在地: 成功表示・文言はPR #13、画面shellはPR #14、導入会話・skip・clear反応はPR #15、全Stageの概念案内とエラー表示、workspace状態保持、Stage 5複数経路、Stage 1〜5証拠→判断→結果カードをmainへ反映済み。次は改善後の内部パイロットとPhase 5合格判定
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
| 4 | 5ステージ完成 | 完了・PR #10までmain反映済み |
| 5 | MVP検証と改善 | 改善単位1〜6完了・main反映済み、改善後の最終検証待ち |
| 6 | Git編拡張の逐次評価 | 未着手 |
| 7 | 将来技術編の再評価 | 未着手 |

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
3. STAGE-GIT-03 stash（完了。案内量削減を先行確認し、読み取り専用状態要約はOFF）
4. STAGE-GIT-04 merge conflict（完了・PR #8マージ済み）
5. STAGE-GIT-05 reflog（完了・PR #10マージ済み）
6. シーズン1全体の物語接続と振り返り整合（完了）

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
- クリア後の最終状態要約と自己確認だけで復旧根拠を考える体験が成立するか。ライブworkspace上の復旧報告は採用しない。
- player resetを3スター条件に含めることが、安全な試行錯誤を妨げていないか。

### 完成条件

- Git初心者であるユーザー1名の内部パイロットと、改善後の対象限定再確認を記録している。Codexと補助エージェントは参加人数へ含めず、結果を一般ユーザーへ一般化しない。
- [`docs/phase-5-validation-plan.md`](docs/phase-5-validation-plan.md)の合格基準を満たし、未解決のCriticalまたはMajorがない。
- 学習効果またはゲーム体験の重大な欠陥を要件へ反映している。

### 次へ進む条件

- Git編を継続改善する価値が確認できる。

## 10. Phase 6 Git編拡張の逐次評価

ゲーム内の将来章は、Chapter 0「日常開発フロー」、現在のStage 1〜5であるChapter 1「事故対応」、Chapter 2「remote共同作業」、Git編Finaleの順を候補とする。ゲーム内の章順と実装順は分離し、Phase 5後に1章ずつ個別設計・承認する。

最初の候補は次のとおりとするが、試作順、remoteの表現方式、課題と評価の単位はPhase 5後に決定する。

1. 初心者へ推奨し経験者がスキップできるChapter 0を、3ステージ・20〜40分の暫定規模で検討する。
2. 他者と共有する開発を扱うChapter 2を検討する。
3. Chapter 0〜2の判断を組み合わせるGit編Finaleを検討する。

上記は承認済みの将来方向であり、許可command、fixture、Runner、snapshot、採点、画面、テストの実装仕様ではない。具体的な設計はPhase 5後に逐次決定する。

その他の高難度候補：

- interactive rebase
- bisect
- remote共同作業
- stash conflict
- detached HEAD
- 複数事故の複合stage

安定版MVPの検証結果をもとに、1stageずつ個別承認する。ランダム生成、汎用stage engine、キャラクター管理機構、将来編を見越した共通Runnerは導入しない。

## 11. Phase 7 将来編の再評価

### Javaコードレビュー編

- 同じ主人公、会社、主力サービス、主要人物を再利用し、指摘を受ける側から根拠を示してレビューする側への成長を候補とする。
- Git編のRunnerやstage実装を無理に共通化しない。

### SQL編

- 同じサービスの架空データを使い、症状から仮説を立てて調査報告を作る成長を候補とする。
- 管理DBと別instance、別network、別credentialを前提に脅威モデルを作り直す。
- Git編との統合、同一repository内module、別applicationを再比較する。

### Docker障害対応編

- 同じサービスの起動・build・deploy障害を扱い、運用担当と再発防止を合意する成長を候補とする。
- Docker操作自体がhost支配につながるため、専用VM級の隔離costを再評価する。

いずれもGit編MVPの完成と検証前には着手しない。

## 12. 現在の次作業

1. Phase 4とSTAGE-GIT-01〜05は完了し、起動・DB不具合の修正はPR #11、成功表示・文言はPR #13でmainへ反映済みである。
2. Phase 5の初回内部パイロットは完了し、明るい固定背景と白いモニターによる画面shell、Stage 1〜5の導入会話、skip・再表示、clear時の人物反応をmainへ反映した。
3. [`docs/phase-5-experience-improvement-plan.md`](docs/phase-5-experience-improvement-plan.md)の改善単位1〜6をmainへ反映した。全Stageの概念案内、状態保持、Stage 5複数経路、証拠→判断→結果カードまで実装済みである。
4. 改善後はStage 1〜5を再確認し、カードが判断を誘導しすぎないか、復旧根拠の説明に役立つか、既存の共通UI・状態保持・Stage 5の2経路を対象限定で確認する。
5. 新卒研修から後輩を支援する立場までの共通物語骨格は採用済みとする。Chapter 0の3ステージ・20〜40分は暫定規模であり、具体設計はPhase 5完了後にユーザー承認を得る。
6. ライブworkspaceの完了を宣言する復旧報告、`POST /report`、report待機用状態機械、TTL、sweeperは採用しない。自動clearと即時cleanupを維持する。

詳細は[`docs/requirements.md`](docs/requirements.md)、[`docs/game-design.md`](docs/game-design.md)、[`docs/git-mvp-stages.md`](docs/git-mvp-stages.md)、[`docs/architecture.md`](docs/architecture.md)、[`docs/test-strategy.md`](docs/test-strategy.md)を正本とする。

コード変更では作業branch作成、実装前後レビュー、対象限定テストを行う。commit、push、PR作成、mergeはユーザーが行う。
