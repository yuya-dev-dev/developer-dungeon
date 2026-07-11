# Git編 1日縦切り版仕様

## 文書情報

- 状態: Review-ready（井上P1解消済み、ユーザー確認待ち）
- 上位文書: [`requirements.md`](requirements.md)、[`game-design.md`](game-design.md)、[`threat-model.md`](threat-model.md)、[`architecture.md`](architecture.md)
- 対象ステージ: [`STAGE-GIT-01`](git-mvp-stages.md#3-stage-git-01-公開済み変更を取り消す)

## 1. 目的

1日縦切り版は、完成品の縮小版ではなく、次の仮説を1つの安全なend-to-end経路で確認するためのtimeboxである。

1. 実際のGitコマンドを入力してrepository事故を直す体験が成立するか。
2. command列ではなく最終状態による採点が理解しやすいか。
3. 新人エンジニアの短い物語が、教材感を弱めつつ操作を邪魔しないか。
4. appとGit実行を分離したまま、最小のWeb体験を作れるか。

1日で終えることより、隔離境界を守ることを優先する。

## 2. 完成イメージ

プレイヤーがローカルWeb画面を開くと、公開済みの誤commitを取り消す障害チケットと短い会話が表示される。プレイヤーは`status`、`log`、`show`で状態を調べ、`revert`を実行する。システムは使い捨てcontainer内のGitを動かし、最終状態を検証して1〜3スターを表示する。リセットするとcontainerを破棄して同じfixtureから再開する。

## 3. 含める機能

| ID | 機能 |
|---|---|
| VS-FUNC-001 | `STAGE-GIT-01`の固定fixture |
| VS-FUNC-002 | 1画面のSpring MVC / Thymeleaf UI |
| VS-FUNC-003 | 短い導入会話、障害チケット、技術目標、守るべき条件 |
| VS-FUNC-004 | 1行command入力とserver-side validation |
| VS-FUNC-005 | `status`、`log --oneline`、`show`、`revert --no-edit`のstage別許可 |
| VS-FUNC-006 | 別processのGit Runner controller |
| VS-FUNC-007 | attemptごとのdisposable challenge container |
| VS-FUNC-008 | stdout、stderr、exit code、実行時間の上限付き表示 |
| VS-FUNC-009 | RepositorySnapshotによるSTAGE-GIT-01採点 |
| VS-FUNC-010 | 4段階ヒント |
| VS-FUNC-011 | 累積3スター |
| VS-FUNC-012 | container破棄を伴うリセット |
| VS-FUNC-013 | クリア後の短い技術振り返りと物語上の反応 |

## 4. 含めない機能

- PostgreSQL、Flyway、command history永続化
- ステージ一覧と進捗保存
- STAGE-GIT-02〜05
- コンフリクト用ファイル編集
- ログイン、ユーザー設定、名前入力
- 完全なGit graph GUI
- SPA、WebSocket、リアルタイム更新
- Docker Composeによるアプリ全体の起動
- GitHub Actions
- responsive UIの作り込み
- Java、SQL、Docker学習編

## 5. runtime境界

```text
Browser -> Spring Boot app -> Git Runner controller -> challenge container
```

- appとRunnerは別JVM processとする。
- appとRunnerはloopbackだけにbindする。
- challenge containerはattemptごとに作成する。
- player由来Gitはchallenge container内でだけ実行する。
- appとchallenge containerへDocker socketを渡さない。
- challenge containerへhost directoryをbind mountしない。
- network、privilege、resource limitは[`threat-model.md`](threat-model.md)に従う。
- DBは使用しない。

## 6. 画面

主要画面は1つとする。

### 上部

- 章タイトル
- 2〜4往復程度の導入会話
- 障害チケット
- 技術目標
- 守るべき条件

### 中央

- command入力
- 実行button
- Git output
- 現在branchとclean状態の読み取り専用要約

### 下部

- 段階hint
- リセット
- 復旧報告／判定
- clear後のスターと振り返り

物語と技術条件を別の視覚領域に置き、会話をskipしても課題条件を確認できるようにする。

## 7. 入力文法

受理する形を次に限定する。

```text
git status
git log --oneline
git show <allowed-unique-short-id-or-full-id>
git revert --no-edit <C2-unique-short-id-or-full-id>
```

- 大文字小文字、余分なoption、省略optionを勝手に補正しない。
- whitespaceの許容規則をparser testで固定する。
- quote、改行、`;`、`|`、`&`、`>`、`<`、backtick、`$()`を拒否する。
- Runnerは観察commandのobject IDを12桁で表示し、appはそのattemptで実際に表示済みの12桁IDまたは完全IDだけを許可する。
- appは短縮IDを完全IDへ正規化し、Runnerはworkspace内objectとstage allowlistを再検証する。
- 曖昧prefix、未知object、revision式を拒否する。
- rejected inputはRunnerへ送らない。

## 8. 初期状態と採点

初期状態とクリア条件は[`git-mvp-stages.md`](git-mvp-stages.md)の`STAGE-GIT-01`を正本とする。

縦切り版では次を必ず検証する。

- 公開済みの不正commit`C2`が履歴に残る。
- HEAD treeが正常な`C1` treeと一致する。
- 新しいrevert commitが作られている。
- indexとworking treeがcleanである。
- revert途中状態ではない。

ファイル内容だけが正常でも、`C2`を履歴から消した状態は不正解とする。

## 9. state管理

- stage定義、hint、expected stateは固定resourceとする。
- 論理attemptはmemoryに保持し、reset後も同じattempt IDを継続する。
- resetではworkspace generationだけを交換し、highest hint level、player reset count、system recovery count、command sequenceを保持する。
- 3スター判定に使用するのはplayer reset countだけとし、timeoutやRunner recoveryではスターを下げない。
- Browser sessionにはworkspace IDそのものを直接公開せず、app側のattempt IDと関連付ける。
- app再起動時の進捗復元は行わない。
- Runner再起動時は孤児containerを固定labelで回収する。

## 10. 最小自動テスト

### Dockerなし

- 許可commandのparse成功
- shell構文、改行、未知option、別object IDの拒否
- clear snapshotの合格
- treeだけ正しいが`C2`が祖先でないsnapshotの不合格
- dirtyまたはrevert途中snapshotの不合格
- hint levelとreset有無による3スター計算
- 表示済み12桁object IDの完全ID正規化と、未表示・短すぎる・曖昧prefix・revision式の拒否
- reset前後でhint level、player reset count、system recovery count、command sequenceが保持されること
- request二重送信、command中resetが一度だけ処理されること
- Git出力と入力中のHTML、script、ANSI、制御文字がDOMとして解釈されないこと

### Dockerあり

- fixtureからcontainerを作成できる
- 許可commandを実Gitで実行できる
- network、mount、user、resource limitが設定される
- timeoutまたはresetでcontainerを破棄できる
- 正しいrevert後のsnapshotがclearになる

Dockerを伴うテストはユーザーの明示許可を得て実行する。

## 11. 最小手動確認

1. appとRunnerを別processで起動する。
2. 画面から`status`、`log`、`show`を実行する。
3. 禁止入力が実行されず、拒否理由が表示されることを確認する。
4. 誤ったGit操作または対象で、Gitエラーと入力拒否が区別されることを確認する。
5. 正しいrevertでclearになることを確認する。
6. hint level 3・4とresetがstarへ反映されることを確認する。
7. reset後に初期状態へ戻ることを確認する。
8. clear／reset後にcontainerが残らないことを確認する。
9. 会話をskipしても課題条件を理解できることを確認する。

## 12. 完成条件

1. VS-FUNC-001〜013が1つの画面から動作する。
2. Dockerなしの対象限定unit testが成功する。
3. ユーザー許可を得たDocker integration testと手動確認が成功する。
4. [`threat-model.md`](threat-model.md)の1日版向け受け入れ条件を満たす。
5. host Git実行、shell実行、host bind mount、challengeへのDocker socket公開が存在しない。
6. 未完成点と再評価結果を[`../roadmap.md`](../roadmap.md)へ反映できる。

## 13. 1日で終わらない場合

時間内に完成しない場合は、次の順で削る。

1. CSSと演出の作り込み
2. 状態要約の表示項目
3. hint level 1・2の文章量
4. clear後の会話量

次は削らない。

- app / Runner process分離
- disposable challenge container
- command allowlistと二重検証
- network、mount、privilege、resource limit
- state-based grading
- cleanup

安全なcontainer実行が未完成の場合、host Git実行へ切り替えず、縦切り版を未完成として停止する。

## 14. 実施後の再評価

- プレイヤーは障害チケットから目標を理解できたか。
- 最初に状態を調査しようとしたか。
- Gitの生出力を読めたか。
- state-based gradingの理由を理解できたか。
- 物語が邪魔、薄すぎる、または正解誘導になっていないか。
- 1ステージの長さが適切か。
- Runner起動時間とreset待ち時間が体験を損なっていないか。
- 5ステージへ拡張できる境界になっているか。

再評価結果によって要件を変更する場合は、先に[`requirements.md`](requirements.md)を更新する。
