# Developer Dungeon 要件定義

## 文書情報

- 状態: Git編とJavaクラス設計問題集MVPは実装済み、PostgreSQL実習問題集はPhase 1設計確定案
- 対象: Git編、Javaクラス設計問題集MVP、PostgreSQL実習問題集MVP
- 上位ルール: [`../AGENTS.md`](../AGENTS.md)
- 関連文書: [`game-design.md`](game-design.md)、[`git-mvp-stages.md`](git-mvp-stages.md)、[`java-class-design-practice.md`](java-class-design-practice.md)、[`sql-practice.md`](sql-practice.md)、[`threat-model.md`](threat-model.md)、[`architecture.md`](architecture.md)、[`phase-5-experience-improvement-plan.md`](phase-5-experience-improvement-plan.md)

## 1. この文書が決めること

この文書は、Developer Dungeonの現在のプロダクト目的、対象ユーザー、Git編の機能要件、非機能要件、MVP範囲、完成条件、Javaクラス設計問題集、およびPostgreSQL実習問題集の上位要件を定める正本である。

この文書では、Javaクラス、HTTP API、DBカラム、Dockerオプションなどの実装詳細は決めない。実装方法はアーキテクチャ文書、Git教材の内容はゲーム設計とステージ仕様、Java問題集は[`java-class-design-practice.md`](java-class-design-practice.md)、SQL問題集は[`sql-practice.md`](sql-practice.md)で定める。

### 表記規則

日本語本文では「プレイヤー」「ステージ」「コマンド」「コンテナ」「クリア」を基本表記とする。Java型、HTTP field、DB値、ファイル名などの実装識別子は、`GitCommand`、`stageKey`のようにcode表記する。

## 2. 確定しているプロダクト方針

### 2.1 現在の対象

- Gitコマンドの隔離実行・状態採点基盤はGit編専用とする。Javaクラス設計問題集と次期PostgreSQL実習問題集は同じリポジトリとSpring Boot applicationへ独立境界で追加するが、この基盤を再利用または一般化しない。
- 初回はローカル専用・シングルプレイヤーとする。
- プレイヤーは実際のGitコマンドを入力して課題を解く。
- クリア判定は入力したコマンド列ではなく、最終的なリポジトリ状態を基本とする。
- 1日で試す縦切り版と、複数日以上をかける安定版MVPを分ける。
- 実際のGit CLIは、1日縦切り版を含め、課題ごとの使い捨て隔離環境でのみ実行する。
- セキュリティ上成立しない設計は、面白さや開発速度より先に却下する。

### 2.2 世界観

- 主人公は新人エンジニアとする。
- 主人公は同じ架空の開発会社に所属し、新人研修、仮配属、チーム開発、障害対応を通じて責任範囲を広げる。
- 舞台は1つの架空の主力サービスを中心とし、複数のサブシステム、チーム、リポジトリを経験できる構造とする。具体的な組織図やリポジトリ構成は現時点で固定しない。
- 各ステージは、現場で発生したGit事故を調査・復旧する1つのエピソードとする。
- 技術用語とGitコマンドは現実の名称を使い、物語上の別名へ置き換えない。
- 物語は問題の背景、人物関係、失敗の影響、主人公の成長を担い、正解コマンドを直接教える役割にはしない。
- 章が進むごとに、主人公が自分で確認事項、復旧方針、関係者への説明を決める範囲を広げ、最終的には後輩へ判断根拠を問いかけられる立場へ成長させる。

### 2.3 将来構想

現在の`STAGE-GIT-01`〜`05`を、Git事故対応を扱うChapter 1として位置づける。Chapter 0「Git基礎研修」は初心者へ推奨する任意の導入章として、local repositoryの基本を3研修で広く浅く扱う。経験者はChapter 1から開始できる。remote共同作業を扱うChapter 2と、複数事故を分解して解決するGit編Finaleは将来候補とする。

Javaクラス設計問題集は、Git編MVP後の拡張として実装済みである。Developer Dungeonの上位ブランドと編選択画面は共有するが、Git編の事故対応、Runner、状態採点、スター、物語演出を適用せず、要求仕様を読んでVS Code上で複数クラスを設計・実装する問題集として独立した学習体験を持つ。上位要件は本書15章、9問の内容と実装境界は[`java-class-design-practice.md`](java-class-design-practice.md)を正本とする。

PostgreSQL実習問題集は、Git編とJava編に続く次期拡張としてPhase 1設計を承認する。上位brandと編選択を共有するが、Git編の物語、会話、スター、Git Runnerを再利用せず、Browser上のSQL editorと専用の隔離PostgreSQLを使う独立した学習体験とする。MVPはlocal限定とし、具体内容は本書16章と[`sql-practice.md`](sql-practice.md)を正本とする。

Javaコードレビュー編とDocker・CI/CD障害対応編は将来候補である。Javaクラス設計問題集とJavaコードレビュー編は別の学習機能として扱う。Chapter 0の確定仕様は[`chapter-0-training.md`](chapter-0-training.md)を正本とする。Git Chapter 2以降と将来編は1単位ずつ設計し、ユーザーが個別に承認するまで実装範囲へ含めない。

全編で共通化するのは上位brand、編選択、基本navigation、視認性の基準までとする。世界観、人物、チケット、物語上の学習ループはGit編など物語型の教材だけに適用し、Java編とSQL編には必須としない。将来編だけを理由に、`challenge_type`、汎用Runner、プラグイン機構、共通DB構造、シナリオエンジン、キャラクター管理機構を導入しない。

## 3. 対象ユーザー

### 3.1 主対象

- `add`、`commit`、`push`などの基礎操作を学習済みだが、履歴事故の復旧に自信がない初級後半から中級手前の学習者
- コマンドの暗記ではなく、状態の観察と安全な判断を身につけたい学習者
- Gitの実務的な失敗を、安全な環境で試したい学習者

### 3.2 副対象

- 基礎を復習したい経験者
- 将来追加する高難度ステージで、rebase、bisect、remote事故などを練習したい開発者

完全なGit未経験者もChapter 0の副対象に含める。長い講義ではなく、実Gitを使う短い新人研修でlocal repositoryの日常操作と状態モデルを学ぶ。

## 4. プロダクト目標

| ID | 目標 |
|---|---|
| REQ-PROD-001 | プレイヤーがGitリポジトリの状態を観察し、何が壊れているか仮説を立てられる |
| REQ-PROD-002 | プレイヤーが履歴を書き換える操作と履歴を保存する操作を、状況に応じて選択できる |
| REQ-PROD-003 | プレイヤーがGitのエラーを失敗扱いだけで終わらせず、次の調査へ利用できる |
| REQ-PROD-004 | ゲーム内で学んだ判断を、未知のリポジトリ事故へ転用できる |
| REQ-PROD-005 | 実際のコマンド入力と物語上の成長を両立し、単発の問題集に見えない体験を作る |

## 5. ゲーム機能要件

| ID | 要件 |
|---|---|
| REQ-GAME-001 | プレイヤーはステージの障害概要、技術目標、守るべき条件を確認できる |
| REQ-GAME-002 | プレイヤーは1行のGitコマンドを入力できる |
| REQ-GAME-003 | システムは許可されたGit操作と引数だけを受理し、拒否理由を表示する |
| REQ-GAME-004 | システムはGitの標準出力・標準エラー・終了結果を上限内で、HTMLとして解釈されないplain textとして表示する |
| REQ-GAME-005 | プレイヤーは4段階のヒントを必要な段階だけ開示できる |
| REQ-GAME-006 | プレイヤーは同じ論理attemptのヒント使用とplayer reset回数を保持したまま、workspaceだけを破棄・再生成できる |
| REQ-GAME-007 | システムはコマンド履歴ではなく、信頼できるrepository snapshotからクリアを判定する |
| REQ-GAME-008 | クリア後に、修復前後の状態、安全な理由、危険な代替案、固定clear scene、主人公の成長beatを確認できる |
| REQ-GAME-009 | 安定版MVPではステージ進捗、ヒント使用、player reset回数、system recovery回数、コマンド履歴を保存する |
| REQ-GAME-010 | 入力拒否、Gitエラー、タイムアウト、Runner障害を区別して表示する |
| REQ-GAME-011 | コンフリクトステージでは、ステージが指定した通常ファイルだけを限定エディタで編集できる |
| REQ-GAME-012 | Git出力に表示済みのfixture内objectの12桁IDを入力でき、システムが許可済み完全IDへ正規化する |
| REQ-GAME-013 | 同一attemptのコマンド、リセット、判定、破棄を直列化し、同じrequestの再送でGit操作を二重実行しない |
| REQ-GAME-014 | 安定版MVPでは信頼できるrepository snapshotから自動クリアし、クリア後に最終状態要約、非採点・非永続の自己確認、固定解説から復旧完了の根拠を確認できる。active中の選択カードや回答保存は必須としない |
| REQ-GAME-015 | 読み取り専用repository状態と段階ヒントを独立して設定でき、正確なコマンド構文はworkspaceへ常時表示せずヒントレベル3以降で開示する |
| REQ-GAME-016 | プレイ画面は明るい一人称オフィス、中央PC、PC外の人物会話を基本構成とし、狭い画面ではPCと技術条件を優先して表示する |
| REQ-GAME-017 | 初回導入会話はスキップ可能とし、会話を読んでもスキップしても同じ技術目標、attempt、採点条件へ到達する。中央headerへ統合した障害説明と目標だけでも、発生事象、困っている関係者、守る条件、対応が必要な理由を理解できる |
| REQ-GAME-018 | 自動クリア後は最初のviewportで成功を明示し、command formを表示せず、最終状態、自己確認、振り返り、人物の反応へ移る |
| REQ-GAME-019 | 入力拒否と通常のGitエラーでは同じworkspaceと論理attemptを継続し、明示resetまたは結果を安全に確定できないsystem recoveryだけがworkspaceを再生成する |
| REQ-GAME-020 | 全ステージで正確な構文をworkspaceへ常時表示せず、汎用的な用途はStage非依存のcommand参照、正確な構文はヒントレベル3、対象を含む具体手順はレベル4で開示する。server側のcommand allowlistは常に維持する |
| REQ-GAME-021 | 採点は最終snapshotを正本とし、同じ安全な最終状態へ到達する複数の調査順序を許容する。固定コマンド列、最短手順、コマンド数を採点しない |
| REQ-GAME-022 | active Stage画面は、4項目のsidebar、障害説明と目標を含む中央header、現在repository状態、workspaceを基本領域とし、同じ説明、常時概念chip、active中の証拠選択カード、main領域下部のhint cardを重複表示しない |
| REQ-GAME-023 | sidebarの「コマンド」から、番号、コマンド、用途の3列でGitコマンドを学習順に確認できる読み取り専用ページへ移動できる。Stage固有object ID、branch名、file名、正解手順は掲載しない |
| REQ-GAME-024 | sidebarの「ヒント」から現在Stageのヒントを段階開示でき、JavaScript有効時はcommand、hint、reset、限定editor保存の後もページ全体を再読込せず、画面位置、拡大状態、操作文脈を維持できる。JavaScript無効時も通常form POSTで同じ操作を完了できる |
| REQ-GAME-025 | workspaceの通常表示はcommand入力、実行button、実行結果へ絞る。Stage 4の限定editorはmerge conflict中だけworkspace内へ追加し、Stage 4以外、競合前、clear後には表示しない |
| REQ-GAME-026 | 現在repository状態は独立した横幅のある領域で表示し、完全HEAD object IDを等幅かつ折り返さず確認できる。狭い画面では当該領域内だけの横scrollを許容する |
| REQ-GAME-027 | `/`はタイトル兼編選択画面とし、実装済みのGit編とJavaクラス設計問題集の固定cardを表示する。SQL編cardはPhase 2で`/sql/problems`が利用可能になる変更と同時に追加し、それまでは表示しない。閲覧時にDB、attempt、Runner、workspaceへアクセスしない |
| REQ-GAME-028 | `/git/stages`のChapter 1区画は固定のSTAGE-GIT-01〜05を学習順に表示し、各行には番号、現場番号、題名、clear／未clear状態だけを表示する。最高スターは採点・永続化に残しても一覧には表示しない |
| REQ-GAME-029 | タイトル画面とGit編ステージ選択画面は承認済み参照画像の明るいオフィス、研修カード、ホワイトボード型一覧、最小限の情報階層をHTML/CSSで再現し、画像内の文字や透明なclick領域へ操作を依存させない |
| REQ-GAME-030 | 既存の固定Stage URLは維持し、直接アクセスを禁止しない。入口画面の閲覧だけでattemptやworkspaceを作成せず、Stage開始時に限って既存lifecycleを開始する |
| REQ-GAME-031 | `/git/stages`はGit基礎研修とGit事故対応を分け、`TRAINING-GIT-01`〜`03`と`STAGE-GIT-01`〜`05`をそれぞれ学習順に表示する。経験者のChapter 1直接開始と後から研修へ戻る導線を維持する |
| REQ-GAME-032 | Chapter 0は完了／未完了だけを表示し、hint・resetに関係なく内部starsを常に1として保存する。固定手順、最短手順、調査回数は採点しない |

## 6. 評価要件

評価は累積3スター方式とする。

| スター | 条件 |
|---|---|
| 1 | 最終状態のクリア条件を満たす |
| 2 | 1スターに加え、コマンドの形または正解を示すヒントレベル3・4を使用していない |
| 3 | 2スターに加え、プレイヤーが明示的なリセットを使用していない |

所要時間、観察コマンド数、通常のGitエラー、試行錯誤の回数は減点対象にしない。保存する評価は各ステージの最高到達スターとする。

クリア後の自己確認は振り返りの一部であり、スター、進捗、理解度scoreへ影響させず、回答をDBへ保存しない。

リセットは同じ論理attempt内でworkspaceだけを交換する。`highest_hint_level`、`player_reset_count`、`system_recovery_count`、コマンド順序はworkspace交換前後で引き継ぐ。スター判定に使用するのはプレイヤーが明示した`player_reset_count`だけとし、Runner再起動、timeout、応答不明などによるsystem recoveryでスターを下げない。プレイヤーが明示的に新しい挑戦を開始した場合だけ、新しい論理attemptとして評価を0から始める。

## 7. 難易度要件

| 難易度 | 内容 | MVP |
|---|---|---|
| 研修 | 1つの概念へ集中し、観察箇所を明示する | 含む |
| 実務 | 複数の状態を観察し、安全な操作を選ぶ | 含む |
| 修羅場 | rebase、bisect、remote事故など、複合的な判断を求める | MVP後 |

安定版MVPはChapter 0の基礎研修3件とChapter 1の事故対応5ステージで構成し、難易度を自動調整する仕組みは作らない。

## 8. 1日縦切り版

| ID | 要件 |
|---|---|
| REQ-VS-001 | `STAGE-GIT-01`のrevert課題1件だけを提供する |
| REQ-VS-002 | 問題文、短い導入会話、コマンド入力、出力、ヒント、判定、リセットを1画面へ配置する |
| REQ-VS-003 | 実Gitを使い捨てコンテナ内で実行する |
| REQ-VS-004 | DBを使わず、状態はメモリと使い捨てworkspaceだけに保持する |
| REQ-VS-005 | ホスト実行、複数ステージ、ログイン、スコア永続化、リッチな可視化を含めない |
| REQ-VS-006 | 1日で完成しない場合も隔離を弱めず、未完成点を記録して再評価する |

## 9. 安定版MVP

| ID | 要件 |
|---|---|
| REQ-MVP-001 | Chapter 0として`TRAINING-GIT-01`から`TRAINING-GIT-03`までの3研修、Chapter 1として`STAGE-GIT-01`から`STAGE-GIT-05`までの5ステージを提供する |
| REQ-MVP-002 | タイトル兼編選択画面、Git編ステージ選択画面、プレイ画面を主要画面とする |
| REQ-MVP-003 | attemptごとに使い捨てのGit実行環境を生成し、終了時に破棄する |
| REQ-MVP-004 | PostgreSQLへattempt、コマンド履歴、クリア進捗を保存する |
| REQ-MVP-005 | Flywayで管理DBのschemaを管理する |
| REQ-MVP-006 | Git RunnerをSpring Bootアプリケーションと別プロセスにする |
| REQ-MVP-007 | 自動テストと手動テストの双方で、Chapter 0の3研修、Chapter 1の5ステージ、隔離制約を確認する |

## 10. 非機能要件

| ID | 要件 |
|---|---|
| NFR-SEC-001 | ユーザー入力をホストOSまたはSpring Bootプロセス上でコマンドとして実行しない |
| NFR-SEC-002 | 任意シェルを起動せず、Git操作を構造化して二重に検証する |
| NFR-SEC-003 | 課題環境からホストファイル、Dockerソケット、管理DB、秘密値へ到達できない |
| NFR-SEC-004 | 課題環境に時間、CPU、メモリ、PID、ファイル容量、出力サイズの上限を設ける |
| NFR-SEC-005 | 課題環境の外部ネットワークを無効化する |
| NFR-SEC-006 | timeout、失敗、リセット、アプリ終了時に一時環境を回収できる |
| NFR-LOCAL-001 | MVPはローカルホストだけにbindし、外部公開を前提にしない |
| NFR-DATA-001 | サンプル、ログ、スクリーンショットには架空の情報だけを使用する |
| NFR-TEST-001 | 入力許可、状態採点、各ステージ、Runner制限に対象限定の自動テストを持つ |
| NFR-UX-001 | Gitの生出力とゲーム演出を分離し、物語を読まなくても技術条件を誤解しない |
| NFR-UX-002 | 背景と会話演出は装飾または補助情報として実装し、keyboard操作、focus移動、contrast、reduced motion、JavaScript無効時の基本操作を損なわない |
| NFR-UX-003 | 通常のStage更新では表示位置を不意に先頭へ戻さず、更新内容をaria-liveで通知し、keyboard focusを失わせない。clear成立時だけ成功表示を優先してfocusと表示位置を移す |
| NFR-WEB-001 | Git出力、fixture、プレイヤー入力、エディタ内容、エラーを常にuntrusted plain textとしてescapeし、CSPでscript実行を制限する |
| NFR-CON-001 | attempt単位の排他制御、状態機械、request ID、永続化の一意制約で並行要求と再送を安全に扱う |

詳細な脅威と対策は[`threat-model.md`](threat-model.md)を正本とする。

## 11. 技術採用時期

| 技術 | 採用時期 |
|---|---|
| Java / Spring Boot / Thymeleaf / JUnit | 1日縦切り版から |
| Docker | 1日縦切り版からGit課題の隔離に使用 |
| PostgreSQL / Flyway / Docker Compose | 安定版MVPから |
| SQL Runner / disposable PostgreSQL challenge | SQL編Phase 2の安全な縦切り版から。Management PostgreSQLとは分離 |
| Testcontainers | 安定版MVPのDB・challenge image統合テストから |
| GitHub Actions | MVP後 |
| SPAフレームワーク / WebSocket | MVPでは不要 |

具体的なバージョンと初期対応環境は、2026-07-11時点の公式サポート状況を確認し、[`architecture.md`](architecture.md)の「実装基準バージョン」と「local起動」に固定した。セキュリティ修正版へ更新する場合は、互換性確認、対象限定テスト、challenge image digestの再固定を行う。

## 12. Git編MVPに含めないもの

- Chapter 2のremote共同作業、Git編Finale
- Javaコードレビュー編、SQL編、Docker障害対応編
- ログイン、複数プレイヤー、ランキング、実績
- 外部公開、クラウドデプロイ、マルチテナント
- 管理画面、ステージ作成UI、プラグイン機構
- 汎用Runner、イベント駆動、分散処理
- 高度なコードエディタ、リアルタイム通信
- プレイヤーがライブworkspaceの完了を宣言する復旧報告、`report`待機用の状態機械とTTL
- rebase、bisect、実在するremoteへの接続
- 完全なGitグラフGUI、ドラッグ操作、自動コマンド生成

## 13. Git編MVP完成条件

1. Chapter 0の3研修とChapter 1の5ステージすべてで、初期化、コマンド実行、状態採点、ヒント、リセット、クリア後の自己確認と振り返りが動作する。
2. 再起動後もクリア進捗と履歴が残る。
3. 各ステージに正解、近似不正解、dirty、操作途中の自動テストがある。
4. 禁止入力がGitまたはシェルへ渡らないことを自動テストで確認できる。
5. timeout後に課題コンテナとworkspaceが残らない。
6. 課題環境からホスト、Dockerソケット、管理DB、外部ネットワークへ到達できない。
7. Chapter 0の全3研修とChapter 1の全5ステージを手動で通し、ヒント、リセット、エラー分類を確認している。
8. ローカル専用という制約と、未対応環境をREADMEに明記している。
9. Git出力とエディタ内容にHTML・script・制御文字を含めてもBrowserで実行・解釈されない。
10. command二重送信、command中reset、応答喪失後の再送でGit操作、履歴、workspace lifecycleが一度だけ確定する。

## 14. 仮定と未確定事項

### 確定事項

- 開発・初期動作環境は、x86_64版Windows 11、WSL 2 backend、Docker Desktop Linux containerに限定する。Docker Desktop以外、Windows container、Windows on Arm、macOS、Linux nativeは初期サポート対象外とする。
- ステージ定義とfixtureを作成できるのは開発者だけで、ユーザーが任意のステージを投入する機能は提供しない。
- 主人公と主要人物は固定キャラクターとし、アバター作成は行わない。
- 将来の共通物語は、同じ会社の主力サービスを複数のサブシステムとチームが支え、新人の主人公が研修から後輩を支援する立場へ成長する構造とする。
- 現在の`STAGE-GIT-01`〜`05`はChapter 1の事故対応として維持し、将来の章構成だけを理由に技術仕様、採点、Runnerの安全境界を変更しない。
- Phase 5はGit初心者であるユーザー1名の内部パイロットと改善後の対象限定再確認で判定する。Codexと補助エージェントは人間の参加人数へ含めず、結果を一般ユーザーへ統計的に一般化しない。成功閾値は[`phase-5-validation-plan.md`](phase-5-validation-plan.md)を正本とする。

### 実装前に確定する事項

| ID | 未確定事項 |
|---|---|
| TBD-001 | プロダクト、Git編、主人公、会社、主要人物の正式名称 |
| TBD-004 | 5ステージの文章、fixture内のファイル名、コミットメッセージの最終表現 |
| TBD-007 | Chapter 2とGit編Finaleのステージ構成、remoteの表現方式、安全境界、採点方法 |
| TBD-008 | Javaコードレビュー編、Docker・CI/CD編の順序と、各編専用の実行・隔離方式 |

未確定事項は実装上の重大な分岐が発生する前にユーザーへ確認する。推測で確定しない。

## 15. Javaクラス設計問題集

### 15.1 目的と位置づけ

- Java入門書を終えた学習者が、問題演習を通してJava特有のクラス設計へ慣れることを目的とする。
- `main` method内で完結するアルゴリズム問題ではなく、要求仕様からクラス、責務、field、constructor、method、クラス間の関係を考える問題を扱う。
- paiza等のアルゴリズム練習を代替せず、図書館貸出、自動販売機、ショッピングカートなど、複数のオブジェクトが協調する仮想システムの設計を中心とする。
- 問題文と進捗はDeveloper Dungeon内で管理し、利用者自身の実装、compile、採点、設計レビューはVS Code等のlocal環境と外部のChatGPTを使って行う。各問題には、設計したclassを`Main` methodから利用して正常系・失敗系・失敗後の状態を確認する必須scenarioと、教材側が管理する固定の模範`Main.java`を用意する。
- Java編を事故対応の物語へ無理に合わせない。上位ブランド、編選択、基本的な視覚規則は共有してよいが、問題の理解を妨げる物語、会話、スター評価を必須にしない。

### 15.2 学習範囲

問題は、Javaの文法を個別に暗記することではなく、次の要素を要求仕様に応じて組み合わせる力を扱う。

- classとinstance
- private field、constructor、getter、振る舞いを表すmethod
- 責務の分割とクラス間の協調
- encapsulationと不正状態の防止
- staticの適切な用途
- compositionとinheritanceの選択
- interface、abstract class、polymorphism
- exceptionと入力・状態の検証
- collectionを使った複数objectの管理
- 複数の妥当解を比較できる総合設計

### 15.3 難易度

難易度は初級、中級、上級の3段階とする。同じ題材を複数の級で扱ってよく、級が上がるほど仕様、制約、例外、関係する責務を増やす。

| 難易度 | 必須方針 |
|---|---|
| 初級 | 設計対象を絞り、問題文で作成するクラス数、各クラスの目的、各クラスが持つメソッド数とその目的、メンバ変数数とその目的を具体的に指定する。Java入門直後でも、指定された枠の中でfieldと振る舞いを対応づけられる難易度とする |
| 中級 | 初級より要求仕様とクラス間の協調を増やす。クラス数、メソッド数、メンバ変数数を原則として正解条件にせず、学習者が責務分割を判断できる余地を持たせる |
| 上級 | 同じ題材でも例外、状態遷移、拡張性、設計上のtrade-offを含む詳細仕様を要求する。唯一のクラス構成を正解として固定せず、設計意図を説明できる問題とする |

初級の数指定は足場かけであり、指定された数と一致することをサイトが自動採点する機能は設けない。中級・上級では、クラス数が多いこと自体を高評価としない。

### 15.4 問題数とテーマ構成

- 最終目標は約20テーマ、全体約50問とする。
- MVPは図書館貸出、自動販売機、ショッピングカートの3テーマ、全9問とする。
- MVPの各テーマに、初級1問、中級1問、上級1問を用意する。
- したがってMVPは、初級3問、中級3問、上級3問で構成する。
- 同一テーマの各級は独立した問題とし、前の級で作成したcodeがなくても開始・完了できる。題材と学習内容は段階的につなげてよいが、級が上がるほど単なる機能追加ではなく、判断すべき責務、制約、設計上の選択を増やす。
- 初級、中級、上級の9問は最初からすべて選択可能とし、完了状態によるunlockを設けない。
- 最終版の20テーマと50問は均等配分を必須とせず、題材と学習効果に応じて各テーマの問題数を決める。

### 15.5 問題に表示する情報

各問題は、必要に応じて次を表示できる構造を持つ。

- 問題タイトル
- 難易度
- 学習テーマ
- 前提知識
- 要求仕様
- 実装条件
- 必須要件
- 任意の発展要件
- 設計時に考えるポイント
- 必要な場合のヒント
- 初期状態では隠された実務的な模範code
- 未着手、学習中、完了の自己管理
- `Main` methodで生成する具体的なinstance、操作順、期待結果、失敗後の不変条件

初級問題では、前項に加えて、クラス数、各クラスの目的、各クラスが持つメソッド数とその目的、メンバ変数数とその目的を明示する。method名、field名、型まで固定する必要がある場合は問題文で明示し、数の指定だけから暗黙に唯一の命名を要求しない。

各問題には、JDK 25でpreview機能を使わずcompileできる、完全な模範codeを1案だけ用意する。模範codeは断片や疑似codeではなく、問題の必須要件を満たす一貫した実装とする。初期状態では閉じた折りたたみ形式とし、利用者が操作した場合だけ展開する。模範codeは唯一解ではなく、特に中級・上級では別の責務分割やクラス構成も許容する。

### 15.6 学習フロー

1. Java編から問題を選ぶ。
2. 要求仕様、条件、設計要件を読む。
3. VS Code等のlocal開発環境で複数のJava fileを作成する。
4. 必要に応じて`javac`、`java`、または利用者自身の開発環境でcompileする。
5. 問題指定の`Main` scenarioを利用者のlocal環境で実行して動作確認する。採点または設計レビューが必要な場合は、問題文と自分のcodeを外部のChatGPTへ渡して行う。
6. サイトへ戻り、進捗を未着手、学習中、完了から選ぶ。
7. 必要に応じてヒントまたは折りたたまれた模範codeを確認する。

### 15.7 Java編MVPに含めないもの

- サイト内コードエディタ
- Javaコードのuploadまたは保存
- サイト内でのcompile、実行、test
- AST解析、自動採点、唯一解判定
- AI APIによる自動レビュー
- サイト内での利用者codeの実行、採点、期待出力判定、JUnit課題
- Git Runner、challenge container、Gitのattempt lifecycleの再利用
- Java問題へ事故対応の物語、会話、スター評価を必須化すること
- 50問すべての初回実装

### 15.8 Java編MVP完成条件

1. 編選択からJavaクラス設計問題集へ移動できる。
2. 図書館貸出、自動販売機、ショッピングカートの3テーマについて、初級、中級、上級を1問ずつ、合計9問から選択できる。
3. 初級3問すべてで、クラス数、各クラスの目的、各クラスが持つメソッド数とその目的、メンバ変数数とその目的が問題文に明示されている。
4. 各問題で要求仕様、実装条件、必須要件、設計時に考えるポイントを確認できる。
5. 各問題にJDK 25かつpreview機能なしでcompileできる実務的な模範codeを1案だけ用意し、最初は閉じた折りたたみ形式で、利用者が選んだ場合だけ確認できる。
6. 各問題の進捗を未着手、学習中、完了で自己管理できる。
7. Java問題の閲覧と進捗更新がGit Runner、workspace、Git課題containerを使用しない。
8. 問題キー、表示順、難易度、必須項目、テーマとの対応に不整合があれば自動テストで検出できる。
9. Git編の既存ルート、進捗、Runner、採点へ回帰がない。
10. 全9問で具体的な`Main` scenarioと模範`Main.java`を確認でき、教材品質テストでは模範`Main.main`が正常終了する。
11. 9問は前の問題の完了状態にかかわらず、最初からすべて選択できる。

### 15.9 Java専用公開版

- Javaクラス設計問題集9問は、PCとDocker Desktopが停止していてもスマートフォンから閲覧できるGitHub Pages版を提供する。
- 公開版の責務は、問題一覧、問題詳細、ヒント、固定模範code、未着手・学習中・完了の自己申告進捗に限定する。
- 公開版の進捗は`localStorage`へ保存し、端末・Browser間では同期しない。保存機能を利用できない場合も問題閲覧を継続でき、そのsessionだけの進捗として扱う。
- 9問のJSONと模範codeはlocal版と同じ固定resourceを正本とし、公開用に別の問題本文を複製管理しない。
- Git編、Git Runner、Docker challenge container、management PostgreSQL、Spring Boot API、認証情報、秘密値を公開成果物へ含めない。
- Git編とJava local版の既存route、DB進捗、安全境界は変更しない。
- 公開版にログイン、server側進捗同期、利用者code保存、自動採点、AI API連携を追加しない。

## 16. PostgreSQL実習問題集

### 16.1 目的と位置づけ

- SQL入門書を一周した学習者が、現在の知識をPostgreSQLへ転用し、初学者から実務基礎までを演習する。
- Data tableと業務要求を読み、Browser上でSQLを記述、実行、修正、判定する学習フローとする。
- SQL文字列の唯一解を求めず、取得結果または実行後のDB状態で判定する。
- Git編と同じタイトル兼編選択、SQL編専用ステージ一覧を使うが、Git事故対応の物語、会話、スター、Git Runnerを適用しない。
- MVPはlocal専用・single playerとし、PCとDocker Desktopが動作している環境で利用する。Internet公開は別Phaseで再評価する。

### 16.2 MVP教材

- 初学者向けチュートリアル1件を用意する。
- 本問題は4章8問、合計34小課題とする。
- 章は基本検索、集計と加工、複数table、更新と設計で構成する。
- 全8問で業務題材と主要技術テーマの組み合わせを変える。
- 各問題は4〜5小課題を持ち、単発のSQL一文だけで完了する薄い問題にしない。
- 全問題を最初から選択可能とし、前問の完了によるlockを設けない。
- 具体的な題材、table、小課題、公開fixture、固定入力、期待する意味上のresult／DB状態、別解判定は[`sql-practice.md`](sql-practice.md) §7.9を正本とする。
- Problemごとにschema、fixture、gradingのversionを固定し、表示内容とSQL Runnerの実fixtureの不一致をbuild時に検出する。

### 16.3 Browser実行

- Browser上にSQL editorを用意する。
- 選択範囲があれば選択部分、なければeditor全体を実行する。
- Result set、column、row数、更新件数、PostgreSQL error、実行制限を区別して表示する。
- 実行と判定を別操作とし、試行のたびに自動完了させない。
- 同じ問題内ではDB状態を維持し、明示resetで初期dataへ戻す。
- 初級は原則1 statementずつ、transaction問題など明示した場合だけ複数statementを許可する。

### 16.4 判定

- 入力SQL文字列、空白、alias、構文の組み立て方を模範SQLと照合しない。
- 要求したcolumnとrowだけを意味比較し、要求外columnや余分な副作用を許すことで偶然合格させない。
- `SELECT`は必要なcolumn、値、重複、`NULL`を比較し、要求された場合だけrow順も比較する。
- `INSERT`、`UPDATE`、`DELETE`は対象rowと非対象rowを含む実行後状態を確認する。
- `CREATE TABLE`はcolumn、型、default、primary key、foreign key、unique、checkの意味を確認する。
- Transactionは成功時と失敗時の最終状態および不変条件を確認する。
- 判定用SQLと期待dataはplayerへ送らず、信頼済み実行経路だけで使用する。

### 16.5 進捗、下書き、模範SQL

- 問題進捗は`NOT_STARTED`、`IN_PROGRESS`、`COMPLETED`とし、management DBへ保存する。
- 入力SQL本文はmanagement DBとserver logへ保存しない。
- SQL下書きはBrowserのversion付き`localStorage`へ問題・小課題単位で自動保存する。
- 実行履歴は現在Browser sessionの直近20件だけとし、server側へraw SQLを永続化しない。
- 各小課題へ最大3段階のhintと、初期状態では閉じた模範SQLを1案用意する。
- 模範SQLは唯一解ではなく、別解を不正解にする根拠へ使わない。

### 16.6 隔離と安全性

- SQL課題用PostgreSQLとmanagement PostgreSQLを別container、別credential、別権限で分離する。
- Player SQLをSpring Boot appのmanagement DB接続へ渡さない。
- Player SQLをhost shell、`ProcessBuilder`、Git challenge containerで実行しない。
- SQL専用Runnerが問題attemptごとの使い捨てPostgreSQL環境を所有する。
- Challenge containerへhost mount、Docker socket、秘密値、management DB networkを渡さない。
- Learner roleはsuperuser、role作成、DB作成、replication、bypass RLS、server file、program実行、extension追加の権限を持たない。
- 問題ごとのstatement範囲、statement timeout、lock timeout、取得row数、出力size、CPU、memory、process数を制限する。
- 終了、reset、timeout、失敗時にcontainerと一時dataを破棄し、削除失敗時に新generationを重ねない。

詳細な脅威と制御は[`threat-model.md`](threat-model.md)、componentとlifecycleは[`architecture.md`](architecture.md)を正本とする。

### 16.7 SQL編MVPに含めないもの

- Internet公開、cloud hosting、login、multi-user
- GitHub Pages上でのSQL実行
- 利用者SQLのserver側保存、共有、ranking
- AI APIによるSQL生成、採点、解説
- 自由なDB、role、extension、server設定操作
- 利用者によるfixture upload、問題作成、CMS
- MySQL等の複数dialect対応
- DB管理、backup、replication、性能tuning、障害復旧
- Git RunnerまたはGit attempt lifecycleの汎用化

### 16.8 SQL編MVP完成条件

1. タイトル画面からSQL編と8問の一覧へ移動できる。
2. チュートリアルでeditor、実行、結果、error、判定、resetを体験できる。
3. 4章8問、34小課題が異なる題材と技術テーマで提供される。
4. Browser SQL editorから隔離PostgreSQLへSQLを実行できる。
5. SQL文字列ではなくresultまたはDB状態で判定し、妥当な別解を許容する。
6. Management DB、host、Docker socket、他attempt、秘密値へのアクセスを防止できる。
7. Reset、timeout、失敗、終了時のcleanupを確認できる。
8. 進捗、下書き、hint、模範SQLが本章の保存境界どおり動作する。
9. Git編とJava問題集のroute、DB、Runner、公開版へ回帰がない。
