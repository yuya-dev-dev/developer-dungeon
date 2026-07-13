# Phase 4 STAGE-GIT-02「間違ったbranchのcommitを移す」実装計画

## 1. 目的と今回の完成条件

STAGE-GIT-02を、STAGE-GIT-01と同じ隔離Runner・attempt永続化・最高スター表示の境界で追加する。プレイヤーは未公開の通知機能commitを誤った`feature/profile`から正しい`feature/notification`へ移し、誤ったbranchを元の`C0`へ戻す。

今回の完成条件は、固定fixture内で許可された`switch`、`cherry-pick`、`reset --hard`だけを構造化して実行でき、2つのbranch tip、現在branch、tree、clean状態、cherry-pick途中状態を採点できることである。ステージ一覧にはSTAGE-GIT-01とSTAGE-GIT-02だけを表示する。

## 2. シナリオと固定fixture

- `C0`：共通の基点。`feature/notification`はここを指す。
- `C1`：通知機能のcommit。`feature/profile`にだけ誤って存在する。
- 初期HEAD：`feature/profile`の`C1`。working treeはclean。
- 課題：`C1`を`feature/notification`へcherry-pickし、`feature/profile`を未公開の`C0`へ`reset --hard`で戻し、最後に`feature/notification`へいる。

challenge imageには`/opt/fixtures/stage-git-02`を追加する。fixtureは固定author、committer、timestamp、branch名でDocker build時に作成し、workspaceはそのread-only fixtureから使い捨てコピーする。Git object IDの12桁prefix一意性は既存fixture build検証へ追加する。

## 3. 許可操作と二重検証

app parserとRunner validatorの双方で、次だけを許可する。

| 操作 | 構造化CommandKind | 固定制約 |
|---|---|---|
| `git status` | `STATUS` | 引数なし |
| `git log --oneline --all --decorate` | `LOG_ONELINE_ALL_DECORATE` | 引数なし |
| `git branch` | `BRANCH` | 引数なし |
| `git show <ID>` | `SHOW` | 表示済みのC0／C1だけ |
| `git switch <branch>` | `SWITCH` | `feature/profile`／`feature/notification`だけ |
| `git cherry-pick <ID>` | `CHERRY_PICK` | 表示済みかつC1だけ |
| `git reset --hard <ID>` | `RESET_HARD` | 表示済みかつC0だけ |

`ID`は12桁または40桁の小文字hexだけをappで受け付ける。workspace作成時に、STAGE-GIT-02 definitionが初期snapshotからC1（profile tip）とC0（notification tip）を不変allowlistとして捕捉する。`LOG_ONELINE_ALL_DECORATE`の出力で実際に表示された12桁だけを記録し、`SHOW`、`CHERRY_PICK`、`RESET_HARD`はその表示済みprefixと不変allowlistから40桁IDへ正規化する。switch後の現在HEADのancestorを再探索しない。

hint 4は画面にC0／C1の12桁prefixを実際に表示し、そのrender時に同じprefixをattemptの表示済み集合へ登録する。log未実行でもhint 4を開いた後は、その画面に示されたC0／C1だけを正規化できる。hint未表示では受け付けない。app再起動後はworkspace recoveryで固定fixtureからC0／C1を再捕捉し、永続化済みhint levelが4なら画面でhint 4を再表示してから同じprefixを登録する。

Runnerはworkspaceごとに保存したstage key、C0、C1を使い、完全IDを再検証する。C1だけを`CHERRY_PICK`、C0だけを`RESET_HARD`へ許可する。`GitCommand`はcanonical JSONを`kind`、nullable `objectId`、nullable `branchName`へ移行し、`SWITCH`のbranch文字列をobject ID fieldへ入れない。STAGE-GIT-01のDTO生成、app parser、Runner validator、Runner execution、近隣unit testを同じ差分で新contractへ移行する。観察commandは両target null、ID commandは40桁`objectId`だけ、`SWITCH`はallowlist済み`branchName`だけとし、null不足、両field指定、kindと不適切なfieldの組合せをappとRunner双方で拒否する。旧wire JSONとの互換層や任意argument listは導入しない。revision式、任意ref、pathspec、remote、option追加、shell構文は受け付けない。

## 4. Snapshotと採点

既存`RepositorySnapshot`へ次を追加する。

- `currentBranch`
- `feature/profile` tip
- `feature/notification` tip
- `cherryPickInProgress`

既存の7項目constructorはunit test互換のため残す。Runnerがsnapshot取得時に、固定workspaceからcurrent branch、固定2 branchのtip、`.git/CHERRY_PICK_HEAD`を安全な構造化値として取得する。任意のlocal refは列挙・DTO化しない。

STAGE-GIT-02のclear条件はすべて満たすこととする。

1. current branchが`feature/notification`
2. `feature/profile` tipが初期C0
3. `feature/notification` tipのtreeが初期C1のtree
4. `feature/notification` tipがC0ではなく、単一parentがC0である
5. indexとworking treeがclean
6. cherry-pick途中状態でない

treeだけの一致、notification tipのparentがC0でない状態、profileにC1が残る状態、notification上の未commit変更、途中状態は不合格とする。スター計算は既存のhint／player reset規則を変更しない。

## 5. 実装構成

STAGE-GIT-01と02で実際に共有するattempt lifecycleを、固定Java stage definitionを受け取る内部use caseへ移す。これは2ステージで重複する開始、command history、hint、reset、clear、recovery、最高スター導出を1箇所に保つためであり、外部plugin、DB stage script、任意stage key、将来mode用抽象化は導入しない。

- 固定Java定義：STAGE-GIT-01とSTAGE-GIT-02だけ
- 共通use case：stage key固定のopen／execute／hint／reset／progressと既存`StagePersistence`
- stage固有：parser allowlist、表示文言、fixture初期状態のcapture、grader、hint、固定route
- Runner：stage keyごとのfixture copy、許可CommandKind、C0／C1 target再検証
- Controller：`/stages/STAGE-GIT-01`と`/stages/STAGE-GIT-02`、および各stage専用の`/commands`、`/hint`、`/reset` POST routeだけを明示的に定義する。stage keyをhidden fieldやrequest parameterから受け取らない。未対応routeは404。
- 一覧：固定2カードを表示し、各cardの最高スターは既存`highestStars(stage_key)`から導出する。ロック解除、player識別、任意stageのroute parameterは追加しない。

一覧閲覧はprogress queryだけを実行し、Runner、workspace、attemptを作らない。DB進捗読取失敗時は0スターへフォールバックせず、Runnerも起動しない。

## 6. UIと物語

STAGE-GIT-02の画面には、次を固定表示する。

- 第2現場／feature branchの取り違え
- 「未共有の通知機能を、正しいbranchへ移し直してほしい」という先輩からの依頼
- 誤commitが未公開であること、`feature/profile`をC0へ戻すこと、現在branchをnotificationへすること
- 許可コマンドの正確な形
- 4段階hint（branch位置の比較、commit移送と巻き戻しの分離、操作順序、具体的なC0／C1 ID）

hint 4で表示するC0／C1の短縮IDは、このattemptで実際に開示した安全な操作対象として扱う。hintを開く前に同じIDを入力しても受け付けない。

STAGE-GIT-01の画面・採点・fixtureは変更しない。Git出力とplayer入力は既存通りplain textとしてescapeし、CSPとCSRFを維持する。

## 7. テスト範囲

必要最低限だけを実施する。

1. app unit：STAGE-GIT-02 parserの許可／拒否、graderのclear／近似不正解、共有use caseの表示済みID・hint 4でのC0/C1開示・request再送・最高スター読取、既存STAGE-GIT-01の新`GitCommand` contract
2. Runner unit：新CommandKindのshape、stage 2のC0／C1・branch allowlist、fixture path選択
3. MVC／template unit：固定2 route、一覧の2カード、各進捗表示、未対応routeの404
4. Dockerあり：STAGE-GIT-02 fixtureからcontainerを作り、許可順序で操作したsnapshotがclear条件を満たすfocused integration test 1 class

Docker testはこのPhase 4の明示依頼に基づき、challenge imageを固定build scriptで再構築した後に対象1 classだけを実行する。このclassはC0／C1の12桁prefix一意性、初期HEAD、固定2 branch tip、clean状態、許可順序でのclear snapshotを確認する。DB Testcontainers、Browser E2E、全テスト、既存stage全件の再実行は行わない。

## 8. 実装しない範囲

- STAGE-GIT-03〜05、stage lock、一覧の検索・filter・汎用管理画面
- login、player table、player別progress、ranking
- DB schema migration、JPA、stage script／plugin system
- remote操作、`push`／`fetch`、任意branch／ref／pathspec、merge／rebase
- Docker／Compose／DB credential境界の変更
