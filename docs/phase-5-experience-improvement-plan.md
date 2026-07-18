# Phase 5 ゲーム体験改善計画

## 文書情報

- 状態: 改善単位1〜7Cはmain反映済み。改善単位7D「最終状態ベースの別解対応」は実装・レビュー・対象限定テスト完了
- レビュー結果: 従来案はバム・井上`PASS`（2026-07-14）。改善単位7A・7Bは物語変更を含まないためバム対象外、井上の実装前レビューはP1なし、P2を本文へ反映して`CONDITIONAL`（2026-07-16）。7Cはユーザー承認済み参照画像と既存境界に基づき、ユーザーの明示指示により井上レビューを省略した（2026-07-17）。7Dは井上の実装前レビューと実装後再レビューで重大な指摘なし、いずれも`PASS`（2026-07-18）
- 対象: `STAGE-GIT-01`〜`STAGE-GIT-05`
- 根拠: 2026-07-14にGit初心者のユーザー1名が全5ステージをプレイした内部パイロット
- 上位文書: [`requirements.md`](requirements.md)、[`game-design.md`](game-design.md)
- 関連文書: [`git-mvp-stages.md`](git-mvp-stages.md)、[`architecture.md`](architecture.md)、[`test-strategy.md`](test-strategy.md)、[`phase-5-validation-plan.md`](phase-5-validation-plan.md)

## 1. 結論

全5ステージの題材と実Gitを使う方式は維持する。一方、現状は暗色の操作画面、正確な許可構文の常時表示、短い固定手順、成功表示の弱さにより、物語への没入と状態から判断する学習が十分に画面へ現れていない。

Phase 5では次を採用する。

1. 明るい一人称オフィス、中央PC、PC外の人物会話を共通画面構成にする。
2. 初回プレイでは、スキップ可能な短い導入会話から障害チケットと操作へ移る。
3. 自動クリア時は画面上部で成功を明確に伝え、入力フォームを表示せず、人物の反応と振り返りへ移る。
4. サーバー側のcommand allowlistは維持し、画面では概念カテゴリだけを常時表示する。正確な構文はヒントレベル3、対象を含む手順はレベル4へ分離する。
5. 入力拒否と通常のGitエラーでは、それ以前の正しい操作を含むworkspaceを自動再生成しない。
6. 採点は最終snapshotを正本とし、同じ安全な最終状態へ到達する複数の調査順序を許容する。
7. ステージを長くする場合は、診断、仮説、復旧、確認の各判断に意味があることを条件とし、固定コマンド数や最短手順を採点しない。

プレイヤーがライブworkspaceの完了を宣言する復旧報告、`POST /report`、report待機状態、TTL延長は導入しない。現行の信頼済みsnapshotによる自動クリアと即時cleanupを維持する。

## 2. 内部パイロットで確認したこと

| ID | 観察 | 採用する対応 |
|---|---|---|
| UX-01 | 全面暗色で、日中の社内よりハッカー風の印象が強い | 明るい固定オフィス背景と中央PCへ再構成する |
| UX-02 | ステージ開始直後に課題文とコマンド入力が出て、登場人物が存在しない | 初回に短い会話sceneを置き、再挑戦では即skipできるようにする |
| UX-03 | クリアしても下へスクロールするまで成功が分からない | 成功見出しを最初のviewportへ表示し、入力を閉じて結果へfocusを移す |
| LEARN-01 | 正確な許可構文を総当たりできる | 常時表示を概念カテゴリに統一し、構文を段階ヒントへ移す |
| LEARN-02 | 5〜6操作の固定手順を暗記でき、解き直しの意味が弱い | 証拠の比較と確認を増やす。ただし無意味なコマンド数は増やさない |
| LEARN-03 | 外部支援なしで初心者が到達できるか未確認 | 改善後にStage 1、2、5を外部支援なしで再確認する |
| STATE-01 | 2手目の失敗で正しい1手目まで失われた | 入力拒否と通常Gitエラーでは同じworkspaceを継続する |
| STATE-02 | Stage 5の2手目が一意に定まらない | 観察順序を固定せず、少なくとも2経路をfixture統合テストで確認する |
| COPY-01 | Stage 2の目標文がbranchと現在位置を混同している | 2つのbranchの最終位置と最後のcheckout状態を別文で示す |
| UX-04 | command、hint、reset、editor保存のたびに全画面が再読込され、scroll位置と操作文脈を失う | 通常formをfallbackとして維持し、JavaScript有効時は既存POST応答の固定領域だけを部分更新する |
| UX-05 | 独立した障害ticket、導入文、証拠カード、概念chip、下部hintが重複し、command入力までのscroll量が多い | active画面をsidebar、統合header、repository状態、workspaceへ整理する |
| UX-06 | sidebarの「コマンド」「ヒント」が実際の学習導線になっていない | コマンド参照ページとsidebar内hint展開へ置き換える |
| UX-07 | ticketとrepository状態の横並びで文章とHEADが細かく折り返される | ticketをheaderへ統合し、repository状態を単独の横幅ある領域にする |
| LEARN-04 | active中の証拠選択カードは正答を回収せず、画面密度に対する学習上の効果が弱い | active/clearの学習カードを撤去し、clear後の最終状態要約、自己確認、固定解説へ一本化する |
| SCOPE-01 | 現場一覧`/`がStage画面と異なる旧レイアウトである | 承認済み参照画像を基準に、タイトル兼編選択とGit編ステージ選択を改善単位7Cとして独立実装する |

全ステージはGeminiと相談しながらプレイしたため、この結果だけで初心者の自力到達や学習効果を合格とは判定しない。初回のStage 3・4を含む記録は技術動作と問題発見の証拠に限定する。改善後はStage 1・2・5を外部支援なしで再確認し、未知の類似状況4問中3問以上で確認方針、復旧方針、守る対象を説明できることを学習転用の内部ゲートとする。

## 3. 目標プレイフロー

1. ステージ選択後、人物アイコン付きの短い会話を表示する。
2. プレイヤーは会話を1つずつ進めるか、まとめてskipする。
3. 中央PCのheaderへ、発生事象、困っている関係者、利用者または業務への影響、守る条件、技術目標を統合して表示する。
4. sidebarからGitコマンド参照または現在Stageの段階ヒントを必要なときだけ開く。
5. プレイヤーはGit出力から仮説を立て、許可範囲内のコマンドを実行する。
6. 入力拒否または通常Gitエラーでも、それまでのworkspace状態を保持して次の判断を可能にする。
7. 信頼済みsnapshotがclear条件を満たしたら、従来どおりworkspaceを破棄して`CLEARED`を確定する。
8. clear応答では入力フォームを描画せず、最初のviewportへ大きな完了表示、スター、最終状態要約を表示する。
9. 最終snapshotから作成した状態要約を読み、非採点・非永続の自己確認で復旧完了の根拠を考えた後、固定解説、人物の反応、主人公の成長beatを表示する。active中の選択カードと回答保存は置かない。

導入会話のskip状態はプレイ画面内だけの表示状態とし、DB、attempt、Runnerへ保存しない。会話をskipしても、中央headerへ統合した障害説明と目標だけで「何が起きたか」「誰が困っているか」「何を守るか」「なぜ対応が必要か」を説明できるようにする。会話は感情、人物ごとの見方、関係性を補う役割とし、技術条件の唯一の出所にしない。JavaScriptが無効な場合は会話を全件表示し、中央headerへ移動できる通常リンクを提供する。

## 4. UI設計境界

- オフィス背景は装飾であり、課題の技術条件を画像内の文字だけへ持たせない。
- PC画面、会話、ボタン、ターミナルは操作可能なHTMLとして実装する。
- 背景は当面1種類の固定素材でよい。人物全身絵、背景差分、アニメーション、BGMは今回含めない。
- 会話進行は同梱した静的JavaScriptと`data-*`属性だけで扱い、外部通信、任意HTML、`eval`、inline event handlerを使わない。
- JavaScriptがなくても、会話、技術目標、操作フォーム、結果を利用できるprogressive enhancementとする。
- clear後は入力要素を単にdisable表示するのではなく、フォーム自体を描画しない。再送可能なcommand操作が残らないことをController側の状態でも保証する。
- clear見出しへfocusを移し、`aria-live`、十分なcontrast、keyboard操作、`prefers-reduced-motion`を考慮する。
- 狭い画面では背景を省略し、PC領域をほぼ全幅にし、人物会話を下部パネルへ移す。
- default構図は承認済み画像程度の白いmonitorサイズを基準とし、viewportが不足する場合は背景内の手、keyboard、周辺小物から先にcropまたは省略する。具体的なpixel寸法は固定しない。

目標構図と現状比較は[`game-design.md`](game-design.md)の画像を正本とする。生成された目標画像内のXP、ランキング、メニュー、具体的な文言は仕様に含めない。

## 5. 学習設計

### 5.1 常時案内とヒント

改善単位1〜6では全5ステージの常時案内を`CONCEPT_ONLY`へ統一した。改善単位7Aではworkspace内の概念chipも撤去し、Stage非依存のcommand参照とsidebarの段階hintへ案内を分離する。サーバー側のtyped command、parser、allowlist、object ID検証は一切緩和しない。

| 表示段階 | 内容 |
|---|---|
| 常時 | 統合headerの障害説明・目標と、Stage方針に応じたrepository状態。正確な構文は表示しない |
| ヒント1 | 最初に観察すべき証拠 |
| ヒント2 | 適用するGit概念と現場上の制約 |
| ヒント3 | プレースホルダー付きの正確な構文 |
| ヒント4 | 対象branch、file、object IDを含む具体手順 |

入力拒否時に「許可コマンド一覧」をそのまま返さない。拒否理由は、構文、引数、対象がこのステージで扱えないことを安全な範囲で示し、必要ならヒントへ誘導する。

正確な構文を常時表示しない原則は維持したまま、sidebarの「コマンド」から読み取り専用のGitコマンド参照ページへ移動できるようにする。表は左から番号、コマンド、用途の3列とし、このゲームで使用する基本commandを学習順に並べる。Stage固有object ID、branch名、file名、解答となる操作順は載せない。表示catalogはpresentation用の固定resourceとして管理し、Runner allowlist、parser、security policyから動的生成しない。

hint本文はmain領域下部へ重複表示せず、sidebarの「ヒント」button直下に展開する。既存の4段階、最高hint levelの保存、starへの影響、Stage別POSTは変更しない。

### 5.2 操作過程の長さ

全ステージへ一律のコマンド数を課さない。Stage 1は単一概念の研修として短くてよい。Stage 2〜5は、初見プレイで診断、仮説、復旧、確認の4段階を体験できることを目標とする。現行の自動clearを維持するため、「確認」はclear後に信頼済み最終snapshotの状態要約を読み、自己確認で復旧根拠を考える段階を指し、clear後に追加のGitコマンドを実行する段階ではない。

操作量は、UIクリック数やコマンド数ではなく「新しい証拠を得て、仮説または次の判断が変わる意味のあるbeat」で比較する。会話送り、画面遷移、既知情報の再表示、自動feedbackはbeatへ数えない。コマンドを追加する場合は、新しい証拠を得る、危険な操作を避ける、または復旧方針を選ぶ役割を必要とする。固定順序、最短手順、コマンド回数、調査コマンドの多さを採点しない。

fixtureの可変化やランダム生成は今回行わない。暗記対策としての周辺commit、branch名、症状の変化は、固定fixtureによる再現性と学習効果を再検証した後の別設計とする。

### 5.3 学習の回収

改善単位6で追加した証拠→判断→結果カードは、第2回内部プレイで、正答と根拠を回収しないままactive画面の密度を上げていると確認した。改善単位7Aでactiveカードとclear後の報告選択カードを撤去し、学習の回収を次の既存要素へ一本化する。

1. clearを確定した信頼済みsnapshotから作る最終状態要約
2. 非採点・非永続の自己確認
3. 固定の根拠と解説
4. 何が壊れ、なぜ安全に直ったかを示す振り返りと人物反応

回答用POST、DB列、理解度score、Runner呼出し、workspace保持は追加しない。Stage固有の証拠と判断を先に漏らすactive promptも置かない。既存の`StageLearningCard`相当の表示専用metadataが他用途を持たない場合は、実装時にdead codeとして同じ改善単位内で削除する。

## 6. 状態と安全上の不変条件

1. `INPUT_REJECTED`ではRunnerを呼ばず、workspace ID、generation、repository状態を変えない。
2. `GIT_ERROR`ではGitが実際に残した状態を同じworkspaceで保持し、attemptを継続する。自動rollbackやworkspace再生成を行わない。
3. プレイヤーが明示したリセットだけをplayer resetとして数え、同じ論理attempt内でworkspaceを再生成する。
4. timeout、Runner応答不明、プロセス再起動など、結果を安全に確定できない場合だけsystem recoveryとして旧workspaceをcleanupし、generationを進める。
5. cleanupに失敗した状態で新workspaceを作らない。
6. clear条件成立後は`CLEARING`を経てcleanupを完了し、`CLEARED`確定後の画面にcommand formを出さない。
7. clear候補を保持してプレイヤー入力を待つ新状態、report route、workspace TTL延長は追加しない。
8. 採点は信頼済みsnapshotだけを使用し、command列や会話skip状態を条件にしない。

## 7. ステージ固有の決定

### 導入会話の内容契約

会話scene実装前に、各ステージの2〜4 beatを次の契約で固定する。通常会話にはGit操作名、実行順序、対象object IDを含めず、正確な技術条件は障害チケット、状態情報、段階ヒントへ置く。

| Stage | 主な話者 | 現場の困り事と人物ごとの見方 | 主人公への引き渡し |
|---|---|---|---|
| 1 | 運用担当、先輩 | 公開済み設定の問題で確認が止まり、運用担当は利用者影響を避けたい。先輩は共有履歴を守る必要だけを示す | 何が変わり、何を残すべきかを証拠から調べる |
| 2 | QA担当、先輩 | 通知機能のレビューが始められず、QAは2つのbranchの最終位置を必要とする。先輩は未共有である制約を示す | 2つのbranchを比較し、安全な移動方針を説明する |
| 3 | 同期、先輩 | 同期はmain上の未完了作業を失うことを心配し、先輩は急いでcommitしないよう促す | working treeとbranchを調べ、作業を保った段取りを決める |
| 4 | QA担当、二つのチーム | 両チームの変更が必要で、QAは片方を消す解決を受け入れられない | 競合箇所と双方の意図を確認し、統合方針を引き取る |
| 5 | 同期、運用担当、先輩 | 同期は作業消失を心配し、運用担当は元の成果物である証拠を求める。先輩は見えているbranchだけで断定しないよう促す | 操作履歴から根拠を集め、復旧と説明を担当する |

### Stage 2

目標文を次へ変更する。

> 通知機能の変更を`feature/notification`へ移し、`feature/profile`を変更前のC0へ戻す。最後に`feature/notification`をcheckoutした状態にする。

C0と対象commitの対応は、画面内のリポジトリ情報またはGit出力から確認できるようにし、目標文だけでobject IDを指定しない。

### Stage 5

2手目を含む観察順序を固定しない。少なくとも次の2種類を許容する。

- `status`や通常履歴を確認してからreflogへ進む経路
- reflogから失われたcommitを見つけ、必要な内容確認と状態確認を前後して進む経路

branch作成前に対象object IDがそのattemptで安全に表示済みであること、branch作成対象が固定C1であること、最終snapshotの全条件を満たすことは維持する。複数経路の許容は任意のrevision式、branch名、object IDを許可することを意味しない。

## 8. 実装を分ける単位

1. **成功表示・文言**: clear表示、command form非表示、Stage 2文言。attempt状態とRunner contractは変更しない。
2. **画面shell**: 明るい固定背景、中央PC、responsive配置。HTML/CSS中心で、技術条件は変えない。
3. **会話scene**: 固定導入会話、skip、clear反応。表示専用JavaScriptだけを追加する。
4. **案内とエラー表示**: 全ステージ`CONCEPT_ONLY`、段階ヒント、入力拒否文言。server allowlistは維持する。
5. **状態保持・複数経路**: 入力拒否とGitエラーの継続、Stage 5の2経路確認。attempt lifecycleとcleanupの回帰を重点確認する。
6. **学習過程の拡張**: Stage 1〜5へ証拠→判断→結果カードとclear後の関係者報告確認を追加する。fixture変更が必要な場合はステージごとに別途承認する。
7. **画面情報設計の簡素化（7A）**: active画面をsidebar、統合header、repository状態、workspaceへ整理し、学習カード、重複ticket、概念chip、下部hintを撤去する。`GET /commands`の参照表、sidebar内hint、Stage 4のmerge conflict中だけ表示する限定editorを追加する。Runner、DB、fixture、採点を変更しない。
8. **同一画面内の部分更新（7B）**: 7Aで固定した領域を前提に、既存POSTと通常form fallbackを保ったprogressive enhancementを追加する。新JSON API、SPA framework、WebSocket、Runner contract、attempt lifecycleは追加しない。
9. **入口2画面化（7C）**: `/`をタイトル兼編選択画面、`/git/stages`をGit編ステージ選択画面とし、承認済み参照画像の明るいオフィスとホワイトボード構成をHTML/CSSで再現する。Git編だけを有効表示し、Stage一覧は番号、現場番号、題名、clear状態に絞る。既存Stage URL、採点、スター永続化、Runner、attempt lifecycleを変更しない。
10. **最終状態ベースの別解対応（7D）**: [`phase-5-7d-alternative-solutions-plan.md`](phase-5-7d-alternative-solutions-plan.md)に従い、Stage固有の最終不変条件を維持した安全な第2経路を全5Stageへ用意する。固定コマンド列、最短手順、コマンド数を採点せず、任意Git実行は許可しない。

7A、7B、7Cはmain反映済みとする。7DはApp、Runner contract、Runner、DB constraintへまたがるため独立PRとし、Stage固有の最終不変条件と安全境界をレビューしてから実装する。

## 9. 受け入れ条件

- 初回導入会話を進めてもskipしても、同じ技術目標とattemptへ到達する。
- 会話をskipした後も、中央headerの障害説明と目標だけで発生事象、困っている関係者、守る条件、対応が必要な理由を説明できる。
- PC幅では明るいオフィスと中央PC、スマートフォン相当幅ではPC優先の画面になる。
- 全ステージで正確な許可構文を常時表示せず、ヒント3・4で段階開示する。
- 入力拒否後もRunnerは呼ばれず、直前のrepository状態が残る。
- 通常Gitエラー後も同じworkspace、generation、attemptを継続できる。
- clear応答の最初のviewportで成功が分かり、command formが存在しない。
- clear後の確認は最終snapshotの状態要約と自己確認で行い、存在しないclear後commandを前提にしない。
- Stage 2の目標文が2つのbranch位置と最後のcheckout状態を区別している。
- Stage 5を少なくとも2つの安全な観察順序でclearできる。
- active画面に証拠選択カード、独立ticket、概念chip、main領域下部hintがなく、sidebar、統合header、repository状態、workspaceから操作できる。
- clear後は最終状態要約、自己確認、固定解説、振り返りだけで復旧根拠を確認でき、回答保存や新しいlifecycleを追加しない。
- `/commands`は番号、コマンド、用途の3列で、Stage固有ID、branch、file、正解順序を漏らさない。
- Stage 4限定editorは`mergeInProgress=true`の未clear時だけworkspace内へ表示し、他Stage、競合前、clear後には表示しない。
- JavaScript有効時のcommand、hint、reset、editor保存で全画面遷移せず、scroll、拡大状態、操作文脈を保つ。JavaScript無効時は通常formで同じ操作が成立する。
- `/`でGit編を選ぶと`/git/stages`へ移動し、`/git/stages`では固定5Stageの番号、現場番号、題名、clear状態だけを表示する。最高スター、XP、ランキング、詳細説明は表示しない。
- 入口2画面の閲覧でattempt、workspace、Runnerを作成せず、既存Stage URLへの直接アクセスとbookmarkが引き続き機能する。
- 承認済み参照画像は構図と情報階層の基準とし、文字、button、Stage行は画像へ焼き込まずHTML/CSSでkeyboard操作できる。
- Stage 1・2・5の対象限定再確認と未知の類似状況4問によって、[`phase-5-validation-plan.md`](phase-5-validation-plan.md)の改訂済み内部ゲートを判定できる。
- clear後の自己確認は非採点・非永続で、DB、Runner、workspace保持を追加しない。
- Git出力、会話、fixture文字列はplain textとしてescapeされ、CSPを維持する。

## 10. 今回決めないこと

- Chapter 0、Chapter 2、Git編Finaleの具体的な実装
- キャラクターの正式名、会社名、サービス名
- ランダムfixture、汎用シナリオエンジン、動的feature flag
- XP、ランキング、実績、ログイン
- 人物全身イラスト、背景差分、アニメーション、BGM
- Git編以外のcard、edition管理DB、汎用edition controller、無効な将来編の予告表示
- SPA framework、WebSocket、部分更新専用JSON API
- 復旧報告、`POST /report`、report待機状態、workspace TTL延長
- Java、SQL、Docker・CI/CD編の実行基盤
