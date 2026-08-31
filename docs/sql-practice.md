# PostgreSQL実習問題集

## 文書情報

- 状態: Phase 1確定
- 対象: SQL編MVPの教材、学習フロー、画面、実行・判定・リセット境界
- 上位文書: [`requirements.md`](requirements.md)
- 関連文書: [`architecture.md`](architecture.md)、[`threat-model.md`](threat-model.md)、[`test-strategy.md`](test-strategy.md)、[`../roadmap.md`](../roadmap.md)

## 1. この文書が決めること

この文書は、Developer DungeonのSQL編として提供するPostgreSQL実習問題集の正本である。MVPの章構成、8問の題材、各問題の小課題、学習フロー、画面情報、実行・判定・リセットの教材上の契約を定める。

この文書では、Java class、HTTP field、DB column、Docker optionなどの実装詳細を決めない。隔離方式は[`architecture.md`](architecture.md)、安全制御は[`threat-model.md`](threat-model.md)、検証方法は[`test-strategy.md`](test-strategy.md)で定める。

## 2. 目的と対象者

SQL編は、SQL入門書を一周した学習者が、与えられたtableと業務上の要求を読み、PostgreSQL上でSQLを実行して結果を確かめる力を身につけるための実習問題集とする。

- SQL標準に近い基礎知識をPostgreSQLへ転用する。
- 単一tableの検索から、集計、join、subquery、data更新、table定義、transactionまで段階的に扱う。
- SQL文字列の暗記ではなく、要求をSQLへ変換し、結果またはDB状態を確認する力を鍛える。
- 初学者から実務基礎までをMVP範囲とし、DB管理者向け運用、性能設計、障害復旧は扱わない。
- Git編の事故対応物語、会話、スター、Git Runnerを無理に適用しない。

## 3. 確定しているMVP範囲

### 3.1 問題数

- チュートリアル1件と、4章8問を実装対象とする。
- 各章に異なる業務題材と技術テーマを組み合わせた2問を置く。
- 各問題は4〜5個の関連する小課題で構成し、単発の薄いSQL問題にしない。
- チュートリアルは問題数と完了率へ含めない。
- 全問題は最初から選択可能とし、前問の完了によるlockを設けない。

12問を先に固定すると、同じ構文を題材だけ変えて反復する問題が増えるため、MVPは8問へ絞る。利用後に学習上の空白が確認できた場合だけ問題を追加する。

### 3.2 PostgreSQL

- 教材上のSQL dialectはPostgreSQLとする。
- SQL標準とPostgreSQL固有構文の違いがある箇所は、問題文または解説で明示する。
- PostgreSQL固有機能は、基礎を置き換えるのではなく、`ILIKE`、`RETURNING`、identity、transactionなど実務基礎へ必要な範囲だけ扱う。
- PostgreSQL serverの設定、role管理、backup、replication、extension開発はMVPに含めない。

## 4. 学習フロー

1. タイトル兼編選択画面からSQL編を選ぶ。
2. SQL編ステージ一覧で、チュートリアルまたは問題を選ぶ。
3. 問題の業務要求、table定義、初期data、小課題を確認する。
4. Browser上のSQL editorへSQLを入力する。
5. 選択範囲がある場合は選択範囲、ない場合はeditor全体を実行する。
6. 結果table、更新件数、PostgreSQL errorを確認してSQLを直す。
7. 小課題の「判定する」を押し、取得結果またはDB状態を判定する。
8. 小課題を順に進め、すべての必須小課題を満たすと問題を完了する。
9. 必要な場合はhintまたは閉じた模範SQLを開く。
10. 状態を壊した場合は、明示的なresetで問題の最初からやり直す。

問題内では同じ使い捨て演習環境を維持する。前の小課題による更新を次の小課題で利用する問題を許可する。resetすると現在generationのDB状態と小課題判定を破棄して初期化するが、過去に問題を完了した記録は消さない。

## 5. 初学者チュートリアル

### 5.1 題材

架空のカフェメニューを使い、`menu_items` tableの閲覧だけを行う。チュートリアルではdata更新を行わない。

### 5.2 到達目標

1. 問題文、table定義、初期dataの場所を確認する。
2. 用意された`SELECT`を実行する。
3. 表示列を変更する。
4. `WHERE`と`ORDER BY`を追加する。
5. 実行結果の列、行、件数を読む。
6. 意図的な構文errorを直す。
7. 小課題を判定する。
8. resetの意味を確認する。
9. 最初の本問題へ移動する。

チュートリアルは操作箇所を順に案内するが、完成SQLを最初から全文表示しない。各stepで変更する1点だけを示し、error文の最初の原因箇所を読む習慣をつける。

## 6. 章と問題マトリクス

| 章 | 問題 | 業務題材 | 主な技術テーマ | 小課題数 |
|---|---|---|---|---:|
| 1 基本検索 | `SQL-01` 書店の商品検索 | 書籍catalog | `SELECT`、alias、`WHERE`、`ORDER BY`、`LIMIT` | 4 |
| 1 基本検索 | `SQL-02` 倉庫の在庫確認 | 商品在庫 | 論理条件、`NULL`、`BETWEEN`、`IN`、計算列、`CASE` | 4 |
| 2 集計と加工 | `SQL-03` カフェの売上集計 | 注文明細 | aggregate、`GROUP BY`、`HAVING`、丸め | 4 |
| 2 集計と加工 | `SQL-04` 問い合わせSLA分析 | support ticket | 日時、`CASE`、subquery、CTE | 4 |
| 3 複数table | `SQL-05` 図書館の貸出状況 | 図書館貸出 | inner／left join、未貸出、延滞、複数table | 4 |
| 3 複数table | `SQL-06` EC注文分析 | 注文・商品・顧客 | 3table join、集計、相関subquery、CTE | 4 |
| 4 更新と設計 | `SQL-07` イベント予約管理 | 予約枠と申込 | `CREATE TABLE`、constraint、`INSERT`、`UPDATE`、`DELETE`、`RETURNING` | 5 |
| 4 更新と設計 | `SQL-08` 銀行口座振替 | 口座と振替履歴 | transaction、複数更新、`COMMIT`、`ROLLBACK`、不変条件 | 5 |

合計は8問34小課題とする。問題間で業務題材を使い回さず、同じ構文を扱う場合も、検索、集計、結合、更新など判断対象を変える。

### 6.1 問題ごとのstatement範囲

| 対象 | 許可するroot statement |
|---|---|
| Tutorial、`SQL-01`〜`SQL-06` | 単一`SELECT`。`WITH`は`SELECT`へ帰着するread-only CTEだけ |
| `SQL-07-01` | 指定schema内の単一`CREATE TABLE` |
| `SQL-07-02` | 単一`INSERT`、`RETURNING`可 |
| `SQL-07-03` | 単一`UPDATE`、`RETURNING`可 |
| `SQL-07-04` | 単一`DELETE`、`RETURNING`可 |
| `SQL-07-05` | Player statementなし。判定時に教材側が固定transaction内でconstraintを検証し、必ずrollbackする |
| `SQL-08-01` | 単一のread-only `SELECT` |
| `SQL-08-02` | 先頭`BEGIN`、指定tableへの`UPDATE`／`INSERT`を1〜4個、末尾`COMMIT`からなる最大6 statementの1 script |
| `SQL-08-03` | 先頭`BEGIN`、指定tableへの`UPDATE`／`INSERT`を1〜4個、末尾`ROLLBACK`からなる最大6 statementの1 script |
| `SQL-08-04` | 先頭`BEGIN`、残高check違反を起こす指定`UPDATE`、必要な後続操作、末尾`COMMIT`を構文上持つ最大6 statement。Errorで終端には到達しない |
| `SQL-08-05` | 先頭`BEGIN`、指定tableへの`UPDATE`／`INSERT`を1〜4個、末尾`COMMIT`からなる最大6 statementの1 script |

`EXPLAIN`、`VACUUM`、`ANALYZE`、`TRUNCATE`、schema／database／role操作、function／procedure、psql meta commandはMVPで許可しない。`WITH`の中にdata変更statementを隠す形もread-only問題では拒否する。

## 7. 問題仕様

### 7.1 `SQL-01` 書店の商品検索

**前提table:** `books`

1. 必要な列だけをalias付きで表示する。
2. 価格帯、在庫、categoryを組み合わせて絞り込む。
3. 価格と発売日の複数条件で並べ替える。
4. 条件に合う上位件数だけを取得する。

並び順を要求した小課題では順序も判定する。それ以外は行集合で判定する。

### 7.2 `SQL-02` 倉庫の在庫確認

**前提table:** `inventory_items`

1. `NULL`の棚番号を持つ未配置商品を抽出する。
2. 複数倉庫と数量範囲を`IN`、`BETWEEN`で絞り込む。
3. 単価と数量から在庫評価額を計算する。
4. 在庫数から補充区分を`CASE`で表示する。

`NULL`を`= NULL`で比較しない理由を解説へ含める。

### 7.3 `SQL-03` カフェの売上集計

**前提table:** `menu_items`、`order_lines`

1. 日別の売上額と販売点数を集計する。
2. category別の平均単価と売上額を集計する。
3. 一定額以上のgroupだけを`HAVING`で残す。
4. 最も売上の高いcategoryを丸めた平均値とともに表示する。

`WHERE`と`HAVING`の適用時点の違いを学習観点とする。

### 7.4 `SQL-04` 問い合わせSLA分析

**前提table:** `support_tickets`

1. 受付日時と完了日時から対応時間を求める。
2. `CASE`でSLA内、超過、未完了を分類する。
3. 全体平均より対応時間が長い完了ticketをsubqueryで抽出する。
4. CTEで担当者別の集計を作り、超過率の高い担当者を表示する。

日時はPostgreSQLの`timestamp`と`interval`を使い、timezoneをまたぐ問題はMVPで扱わない。

### 7.5 `SQL-05` 図書館の貸出状況

**前提table:** `members`、`books`、`loans`

1. 貸出中の本と利用者をinner joinで表示する。
2. 現在一度も貸し出されていない本をleft joinで探す。
3. 返却期限を過ぎた貸出を利用者連絡先とともに表示する。
4. 利用者別の現在貸出冊数を、0冊の利用者も含めて集計する。

join条件を`WHERE`へ誤って移してouter joinをinner join相当にしないことを学習観点とする。

### 7.6 `SQL-06` EC注文分析

**前提table:** `customers`、`products`、`orders`、`order_lines`

1. 注文ごとの顧客、商品、数量、小計を表示する。
2. 顧客別の注文回数と購入総額を集計する。
3. category平均より高い商品を相関subqueryで抽出する。
4. CTEで月別売上を作り、前月との差を表示する。

window関数は発展hintで紹介してよいが、必須解にはしない。

### 7.7 `SQL-07` イベント予約管理

**初期状態:** 参照用`events` tableだけを用意し、学習者が`reservations` tableを作成する。

1. identity、primary key、foreign key、check、uniqueを持つ予約tableを作る。
2. 正常な予約を`INSERT ... RETURNING`で登録する。
3. 予約人数を上限内で更新する。
4. cancellation対象だけを条件付きで削除する。
5. 判定buttonから固定constraint testを実行し、違反dataが拒否され、正常dataが残ることを確認する。

模範SQLのconstraint名は唯一解とせず、schemaの意味で判定する。

### 7.8 `SQL-08` 銀行口座振替

**前提table:** `accounts`、`transfer_history`

1. 振替前の残高と送金条件を確認する。
2. `BEGIN`から`COMMIT`までに出金、入金、履歴追加をまとめる。
3. Tentativeな振替を`ROLLBACK`し、全tableが実行前へ戻ることを確認する。
4. 残高constraintに反する振替でtransaction全体が失敗し、部分更新が残らないことを確認する。
5. 成功した振替後も総残高と履歴の整合性が保たれることを確認する。

同時更新、isolation level、deadlock、`SELECT ... FOR UPDATE`は解説で入口を示してよいが、MVPの必須判定には含めない。

### 7.9 決定的なfixtureと判定契約

Phase 2以降は、この節の公開schema、fixture、固定入力、期待する意味上の結果を教材契約とする。表記したrowはすべて架空dataである。各問題は`schemaVersion`、`fixtureVersion`、`gradingVersion`を持ち、初版はすべて`v1`とする。Appの表示resourceとSQL Runnerの実fixtureは、公開schema・fixtureを正規化したfingerprintで対応を検証する。秘密の追加判定dataとtrusted grading SQLはBrowserへ送らない。

Query課題はSQL文字列を比較せず、期待column名、型、row値、重複数を比較する。並び順を明示した課題だけrow順も比較し、それ以外はrow集合として比較する。要求外column、欠落column、余分なrow、欠落rowは不合格とする。`numeric`は数値として比較し、この節で桁数を指定した表示だけscaleも確認する。DML／DDL／transaction課題はresult textではなくtrusted inspectionによるobjectとDB状態を比較し、対象外tableへの副作用があれば不合格とする。

#### 7.9.1 `SQL-01` 公開契約

- Version: `sql-01-schema-v1`、`sql-01-fixture-v1`、`sql-01-grading-v1`
- Schema: `books(book_id integer primary key, title varchar(80) not null, category varchar(20) not null, price integer not null check (price >= 0), stock integer not null check (stock >= 0), published_on date not null)`
- Fixture: `(1,'PostgreSQL入門','DB',3200,5,'2024-04-10')`、`(2,'SQLレシピ','DB',2400,0,'2023-11-01')`、`(3,'Java設計入門','Java',2800,3,'2025-02-15')`、`(4,'Git実践','Tools',1800,7,'2022-08-20')`、`(5,'Web入門','Web',1200,2,'2024-06-01')`、`(6,'アルゴリズム基礎','Java',1500,0,'2021-01-10')`、`(7,'データモデリング','DB',2600,2,'2024-09-12')`

| 小課題 | 固定要求 | 期待する意味上の結果 |
|---|---|---|
| `SQL-01-01` | `title AS book_title`、`price AS price_yen`だけを表示 | 7冊の`book_title, price_yen`。順不同 |
| `SQL-01-02` | category=`DB`、priceは2000〜3500、stockは1以上 | PostgreSQL入門3200、データモデリング2600の2row |
| `SQL-01-03` | stockが1以上をprice降順、同額時published_on降順 | PostgreSQL入門、Java設計入門、データモデリング、Git実践、Web入門の順 |
| `SQL-01-04` | priceが1500以上かつstockが1以上の上位3件 | PostgreSQL入門3200、Java設計入門2800、データモデリング2600の順 |

#### 7.9.2 `SQL-02` 公開契約

- Version: `sql-02-schema-v1`、`sql-02-fixture-v1`、`sql-02-grading-v1`
- Schema: `inventory_items(item_id integer primary key, sku varchar(16) unique not null, item_name varchar(80) not null, warehouse_code varchar(8) not null, shelf_code varchar(8), quantity integer not null check (quantity >= 0), unit_cost integer not null check (unit_cost >= 0), reorder_point integer not null check (reorder_point >= 0))`
- Fixture: `(1,'A-100','青ノート','WH-A','A-01',20,300,8)`、`(2,'A-110','黒ペン','WH-A',NULL,0,120,10)`、`(3,'B-200','USBケーブル','WH-B','B-03',12,800,5)`、`(4,'B-210','マウス','WH-B','B-04',30,2500,10)`、`(5,'C-300','キーボード','WH-C',NULL,4,4200,5)`、`(6,'A-120','付箋','WH-A','A-02',8,200,8)`

| 小課題 | 固定要求 | 期待する意味上の結果 |
|---|---|---|
| `SQL-02-01` | `shelf_code IS NULL`の商品名と倉庫を表示 | 黒ペン／WH-A、キーボード／WH-Cの2row |
| `SQL-02-02` | WH-AまたはWH-B、quantity 10〜30 | 青ノート20、USBケーブル12、マウス30の3row |
| `SQL-02-03` | 全商品に`quantity * unit_cost AS stock_value` | item_id順に6000、0、9600、75000、16800、1600 |
| `SQL-02-04` | 0=`OUT_OF_STOCK`、1〜reorder_point=`REORDER`、それ以外=`OK` | item_id順にOK、OUT_OF_STOCK、OK、OK、REORDER、REORDER |

#### 7.9.3 `SQL-03` 公開契約

- Version: `sql-03-schema-v1`、`sql-03-fixture-v1`、`sql-03-grading-v1`
- Schema: `menu_items(menu_item_id integer primary key, item_name varchar(80) not null, category varchar(20) not null, unit_price integer not null check (unit_price >= 0))`、`order_lines(order_line_id integer primary key, ordered_on date not null, menu_item_id integer not null references menu_items, quantity integer not null check (quantity > 0))`
- `menu_items`: `(1,'コーヒー','Drink',450)`、`(2,'紅茶','Drink',400)`、`(3,'サンドイッチ','Food',700)`、`(4,'ケーキ','Dessert',550)`
- `order_lines`: `(1,'2026-08-01',1,2)`、`(2,'2026-08-01',3,1)`、`(3,'2026-08-01',4,2)`、`(4,'2026-08-02',2,3)`、`(5,'2026-08-02',3,2)`、`(6,'2026-08-02',1,1)`、`(7,'2026-08-03',4,1)`、`(8,'2026-08-03',1,4)`

| 小課題 | 固定要求 | 期待する意味上の結果 |
|---|---|---|
| `SQL-03-01` | 日別の`total_sales`と`items_sold`、日付昇順 | 08-01=2700／5、08-02=3050／6、08-03=2350／5 |
| `SQL-03-02` | category別のmenu catalog単純平均単価と売上額 | Drink=425.00／4350、Food=700.00／2100、Dessert=550.00／1650 |
| `SQL-03-03` | 売上額2000以上のcategoryだけ | Drink=4350、Food=2100 |
| `SQL-03-04` | 売上最大categoryと小数2桁の平均単価 | Drink、4350、425.00の1row |

#### 7.9.4 `SQL-04` 公開契約

- Version: `sql-04-schema-v1`、`sql-04-fixture-v1`、`sql-04-grading-v1`
- Schema: `support_tickets(ticket_id integer primary key, assignee varchar(40) not null, opened_at timestamp not null, closed_at timestamp, sla_hours integer not null check (sla_hours > 0), check (closed_at is null or closed_at >= opened_at))`
- Fixture: `(1,'Aiko','2026-08-01 09:00','2026-08-01 11:00',4)`、`(2,'Aiko','2026-08-01 09:00','2026-08-01 15:00',4)`、`(3,'Ren','2026-08-01 10:00','2026-08-01 13:00',2)`、`(4,'Ren','2026-08-02 10:00',NULL,2)`、`(5,'Mio','2026-08-02 08:00','2026-08-02 09:30',2)`、`(6,'Mio','2026-08-03 08:00','2026-08-03 13:00',4)`

| 小課題 | 固定要求 | 期待する意味上の結果 |
|---|---|---|
| `SQL-04-01` | 完了ticketの対応時間をhourで算出 | ticket 1=2.00、2=6.00、3=3.00、5=1.50、6=5.00 |
| `SQL-04-02` | `WITHIN_SLA`、`OVER_SLA`、`OPEN`へ分類 | ticket順にWITHIN_SLA、OVER_SLA、OVER_SLA、OPEN、WITHIN_SLA、OVER_SLA |
| `SQL-04-03` | 完了ticket全体平均3.50時間より長いticket | ticket 2=6.00、ticket 6=5.00 |
| `SQL-04-04` | 完了分のSLA超過率が最大の担当者 | Ren、completed=1、over=1、over_rate=100.00の1row |

#### 7.9.5 `SQL-05` 公開契約

- Version: `sql-05-schema-v1`、`sql-05-fixture-v1`、`sql-05-grading-v1`
- Schema: `members(member_id integer primary key, member_name varchar(40) not null, email varchar(120) unique not null)`、`books(book_id integer primary key, title varchar(80) not null)`、`loans(loan_id integer primary key, member_id integer not null references members, book_id integer not null references books, loaned_on date not null, due_on date not null, returned_on date, check (due_on >= loaned_on), check (returned_on is null or returned_on >= loaned_on))`
- `members`: `(1,'Aki','aki@example.test')`、`(2,'Ren','ren@example.test')`、`(3,'Mio','mio@example.test')`、`(4,'Sora','sora@example.test')`
- `books`: `(1,'SQL入門')`、`(2,'Git実践')`、`(3,'Java設計')`、`(4,'Docker基礎')`、`(5,'設計パターン')`
- `loans`: `(1,1,1,'2026-08-01','2026-08-15',NULL)`、`(2,2,2,'2026-08-10','2026-08-25',NULL)`、`(3,1,3,'2026-07-20','2026-08-10','2026-08-09')`、`(4,3,4,'2026-08-05','2026-08-18',NULL)`。教材上の基準日は`2026-08-20`で固定し、system dateを使わない。

| 小課題 | 固定要求 | 期待する意味上の結果 |
|---|---|---|
| `SQL-05-01` | 返却前の本と利用者をinner join | SQL入門／Aki、Git実践／Ren、Docker基礎／Mio |
| `SQL-05-02` | 貸出履歴が一度もない本をleft joinで抽出 | 設計パターンの1row |
| `SQL-05-03` | 基準日より期限が前で未返却、emailも表示 | SQL入門／Aki／aki@example.test、Docker基礎／Mio／mio@example.test |
| `SQL-05-04` | 全利用者の現在貸出冊数、0冊も含む | Aki=1、Ren=1、Mio=1、Sora=0 |

#### 7.9.6 `SQL-06` 公開契約

- Version: `sql-06-schema-v1`、`sql-06-fixture-v1`、`sql-06-grading-v1`
- Schema: `customers(customer_id integer primary key, customer_name varchar(40) not null)`、`products(product_id integer primary key, product_name varchar(80) not null, category varchar(20) not null, price integer not null check (price >= 0))`、`orders(order_id integer primary key, customer_id integer not null references customers, ordered_on date not null)`、`order_lines(order_line_id integer primary key, order_id integer not null references orders, product_id integer not null references products, quantity integer not null check (quantity > 0))`
- `customers`: `(1,'Aki')`、`(2,'Ren')`、`(3,'Mio')`
- `products`: `(1,'キーボード','Peripheral',5000)`、`(2,'マウス','Peripheral',3000)`、`(3,'モニター','Display',20000)`、`(4,'モニタースタンド','Display',10000)`
- `orders`: `(1,1,'2026-06-10')`、`(2,1,'2026-07-05')`、`(3,2,'2026-07-10')`、`(4,3,'2026-08-01')`
- `order_lines`: `(1,1,1,1)`、`(2,1,2,1)`、`(3,2,3,1)`、`(4,3,2,2)`、`(5,4,4,1)`、`(6,4,1,1)`

| 小課題 | 固定要求 | 期待する意味上の結果 |
|---|---|---|
| `SQL-06-01` | order、customer、product、quantity、subtotalを表示 | 6明細。subtotalは5000、3000、20000、6000、10000、5000 |
| `SQL-06-02` | 顧客別の注文回数と購入総額 | Aki=2／28000、Ren=1／6000、Mio=1／15000 |
| `SQL-06-03` | 同category平均価格より高い商品 | キーボード5000、モニター20000 |
| `SQL-06-04` | 月別売上と前月差、月昇順 | 2026-06=8000／NULL、2026-07=26000／18000、2026-08=15000／-11000 |

#### 7.9.7 `SQL-07` 公開契約

- Version: `sql-07-schema-v1`、`sql-07-fixture-v1`、`sql-07-grading-v1`
- Initial schema: `events(event_id integer primary key, event_name varchar(80) not null, capacity integer not null check (capacity between 1 and 100))`
- Fixture: `(1,'SQL勉強会',4)`、`(2,'Java設計会',3)`
- 作成対象: `reservations(reservation_id bigint generated by default as identity primary key, event_id integer not null references events, customer_name varchar(80) not null, seats integer not null check (seats between 1 and 4), status varchar(16) not null check (status in ('CONFIRMED','CANCELLED')), created_at timestamp not null default current_timestamp, unique(event_id, customer_name), check (length(trim(customer_name)) > 0))`

`seats`のcheckは1予約あたり1〜4席を表す。Event全体のcapacityと複数予約の合計を照合する競合制御は、triggerやlockを必要とするためMVP外とし、この問題のconstraint要件へ含めない。

| 小課題 | 固定要求 | 期待する意味上の状態 |
|---|---|---|
| `SQL-07-01` | 上記と同じ意味の`reservations`を作る | column、型、nullability、identity、PK、FK、unique、3種check、defaultがcatalog inspectionで一致。constraint名は不問 |
| `SQL-07-02` | event 1へ`学習者A`／2席／CONFIRMED、event 2へ`学習者B`／1席／CANCELLEDを1つのINSERTで登録 | 2rowが存在し、`RETURNING`にID、event、name、seats、statusを返す |
| `SQL-07-03` | event 1の`学習者A`を3席へ更新 | 対象1rowだけseats=3、他row不変 |
| `SQL-07-04` | CANCELLEDだけを削除 | `学習者B`だけ削除、`学習者A`は残る |
| `SQL-07-05` | 判定buttonで固定constraint probe | 存在しないevent、seats=0、重複event/name、空白name、不正statusを5つの独立したtrusted transactionで1件ずつ試し、順にSQLSTATE 23503、23514、23505、23514、23514で拒否される。各connectionはerror直後に閉じてrollbackし、最後のread-only inspectionで`学習者A`の正常1rowだけが残る |

#### 7.9.8 `SQL-08` 公開契約

- Version: `sql-08-schema-v1`、`sql-08-fixture-v1`、`sql-08-grading-v1`
- Schema: `accounts(account_id integer primary key, account_name varchar(40) not null, balance numeric(12,2) not null check (balance >= 0))`、`transfer_history(transfer_id bigint generated by default as identity primary key, from_account_id integer not null references accounts, to_account_id integer not null references accounts, amount numeric(12,2) not null check (amount > 0), transferred_at timestamp not null default current_timestamp, check (from_account_id <> to_account_id))`
- `accounts`: `(1,'運用口座',100000.00)`、`(2,'予備口座',50000.00)`、`(3,'経費口座',20000.00)`。履歴は0row。小課題は順番に行い、resetしない限り前の合格状態を引き継ぐ。

| 小課題 | 固定要求 | 期待する意味上の状態／結果 |
|---|---|---|
| `SQL-08-01` | account 1と2のID、名称、残高を確認 | 1=100000.00、2=50000.00。DB変更なし |
| `SQL-08-02` | 1から2へ10000.00を出金・入金・履歴追加しCOMMIT | 残高90000.00／60000.00／20000.00、総額170000.00、履歴1row |
| `SQL-08-03` | 2から3へ5000.00を同様に実行してROLLBACK | SQL-08-02後と残高・履歴が完全一致 |
| `SQL-08-04` | 3から1へ25000.00を試し、残高check違反を発生させる | SQLSTATE 23514。connection終了後もSQL-08-02後の残高・履歴と完全一致 |
| `SQL-08-05` | 1から3へ15000.00を出金・入金・履歴追加しCOMMIT | 残高75000.00／60000.00／35000.00、総額170000.00、履歴2row。2履歴のfrom/to/amountが1→2／10000.00と1→3／15000.00 |

`SQL-08-01`は単一read-only `SELECT`とする。`SQL-08-02`と`05`は`COMMIT`、`SQL-08-03`は`ROLLBACK`、`SQL-08-04`は構文上`COMMIT`を末尾へ固定する。いずれも先頭`BEGIN`、許可された1〜4個の操作、固定終端からなる最大6 statementとし、終端後のstatement、nested transaction、`SAVEPOINT`系を許可しない。`SQL-08-04`はconstraint errorで終端へ到達しないことを期待し、SQL Runnerがerror直後にconnectionを閉じてrollbackを確認する。

## 8. 問題に表示する情報

各問題は次を表示する。

- 問題番号、章、難易度、題名
- 業務上の要求と完了条件
- 前提知識と今回の学習テーマ
- table一覧、column、型、constraint、relation
- 初期dataのpreview
- 順序付き小課題一覧と現在選択中の小課題
- SQL editor
- SQL実行、判定、reset
- result set、column名、row数、更新件数、command tag
- PostgreSQL errorのcode、要点、位置。秘密値やserver内部pathは表示しない
- 段階hint
- 初期状態では閉じた模範SQL
- 問題完了状態

問題文、table定義、小課題は同時に常時表示せず、sidebarまたはcompact panelで切り替える。editorと結果を主領域とし、通常のPC viewportでeditor、主要button、結果の先頭を確認できる情報密度にする。

## 9. Editorと実行UX

- 選択範囲がある場合は選択SQL、ない場合はeditor全体を実行する。
- 実行対象をbutton付近へ明示し、選択範囲の誤認を防ぐ。
- 初級は原則1 statementずつ実行する。
- 複数statementは問題が明示した場合だけ許可し、`SQL-08-02`〜`05`では§7.9.8の文法に従う1つのtransaction scriptとして実行する。
- 実行と判定は別操作とする。試行錯誤の実行だけで自動的に問題完了へしない。
- 実行中は同じrequestの連打を防ぎ、cancel可能な場合だけcancel操作を表示する。
- result setは上限まで表示し、打ち切った場合は明示する。
- error時もeditorの入力と現在の演習DB状態を維持する。system failure時は結果不明を明示し、成功扱いしない。
- JavaScript有効時は専用editorを使い、無効または初期化失敗時は通常の`textarea`とform submitでSQLを送れるfallbackを持つ。

Editor実装libraryはPhase 2開始前に、local bundle、keyboard操作、license、既存frontend buildへの影響を比較して確定する。CDN、外部font、実行時外部asset読込は使用しない。

## 10. 判定契約

### 10.1 共通原則

- 入力SQL文字列、空白、alias、join順、subqueryの有無では判定しない。
- 問題ごとの信頼済みinspectorが、実行後のresultまたはDB状態を確認する。
- 模範SQLと異なる妥当な別解を許容する。
- 判定用SQL、期待data、管理用credentialをBrowserへ送らない。

### 10.2 `SELECT`

- column数、意味上必要なcolumn名、値、重複、`NULL`を比較する。
- 並び順が要求に含まれる場合だけrow順を比較する。
- 並び順が要求されない場合はrowのmultisetとして比較する。
- 数値はPostgreSQL上の型とscaleを基準にし、表示formatの違いだけで不正解にしない。

### 10.3 DML

- 更新件数だけでなく、対象row、非対象row、constraint、不変条件をtrusted queryで確認する。
- 偶然期待件数になった全件更新を合格にしない。

### 10.4 DDL

- table名、column、型、NULL可否、default、primary key、foreign key、unique、checkをcatalogから確認する。
- constraint名、column定義順、同等な型表記など、意味を変えない差は許容する。

### 10.5 Transaction

- 成功pathでは出金、入金、履歴、総残高の不変条件を確認する。
- 失敗pathでは部分更新が残らないことを確認する。
- `COMMIT`という文字列の有無ではなく、最終状態で判定する。

## 11. Hintと模範SQL

- 各小課題に最大3段階のhintを持てる。
- Hint 1は確認対象、Hint 2は使用する概念、Hint 3は構文骨格を示す。
- Object名や完成SQLをHint 1で先に示さない。
- 模範SQLは各小課題に1案だけ用意し、初期状態では閉じる。
- 模範SQLを開いても問題への再挑戦を妨げない。スターや減点は設けない。
- 模範SQLには結果だけでなく、なぜその構文を選ぶか、別解があり得る箇所、PostgreSQL固有点を短く添える。

## 12. 進捗、下書き、履歴

- 問題進捗は`NOT_STARTED`、`IN_PROGRESS`、`COMPLETED`の3状態とする。
- 最初のattempt作成で`IN_PROGRESS`、全必須小課題の合格で`COMPLETED`とする。
- 完了状態はmanagement DBへ保存するが、入力SQL本文は保存しない。
- Editor下書きはversion付きkeyでBrowserの`localStorage`へ問題・小課題単位に保存する。
- 保存不能時はmemory内で継続し、下書きが永続化されないことを表示する。
- 実行履歴は現在Browser session内の直近20件だけを保持し、raw SQLをserver logまたはmanagement DBへ永続化しない。
- Resetは現在attemptの実行履歴と小課題合格を破棄する。Browser下書きは確認後に消去する。
- 完了済み問題は何度でも新しいattemptで再演習できる。再演習またはresetで過去の`COMPLETED`進捗を未完了へ戻さない。

## 13. Errorとreset

Errorは次に分ける。

1. **入力拒否:** 問題で許可されないstatement、psql meta command、上限超過。
2. **PostgreSQL error:** 構文、constraint、型、参照先など通常の学習対象となるerror。
3. **実行制限:** timeout、lock timeout、result row、出力、resource上限。
4. **system failure:** SQL Runner、Docker、演習container、management DBの利用不能。

入力拒否ではSQLを実行しない。PostgreSQL errorではDBが実際に残した状態を維持する。system failureで実行結果を確定できない場合は状態不明として同じcontainerでの続行を禁止し、recoveryまたはresetへ案内する。

Resetはplayerが明示した場合だけ行う。現在containerを先に破棄し、破棄成功後だけ新generationを作成する。破棄に失敗した状態で新containerを追加しない。

## 14. MVPに含めないもの

- Internet公開、外部利用者向けhosting、login、multi-user
- GitHub Pages上でのSQL実行
- 利用者SQLのserver側保存、共有、ranking
- AI APIによるSQL生成・採点・解説
- 自由なDB作成、role作成、extension追加、server設定変更
- CSV upload、任意fixture、利用者作成問題、CMS
- ER図editor、schema migration tool、DB管理console
- index設計、`EXPLAIN ANALYZE`による性能課題
- concurrent transaction、deadlock復旧、運用監視、backup／restore
- MySQL、SQLite、Oracle、SQL Serverなど複数dialect対応
- Git Runner、Git challenge container、Git attempt contractの汎用化

## 15. MVP完成条件

1. タイトル画面からSQL編ステージ一覧へ移動できる。
2. チュートリアルと4章8問を最初から選択できる。
3. 8問すべてが異なる業務題材と主要技術テーマを持ち、各4〜5小課題で構成される。
4. Browser上でSQLを入力し、隔離PostgreSQL上で実行できる。
5. Result set、更新件数、PostgreSQL error、実行制限を区別して表示できる。
6. SQL文字列ではなくresultまたはDB状態で判定し、妥当な別解を許容できる。
7. 問題内のDB状態を維持し、明示resetで安全に初期化できる。
8. Management DB、他attempt、host filesystem、Docker socket、秘密値へplayer SQLからアクセスできない。
9. 下書きは`localStorage`、完了進捗はmanagement DBへ保存し、raw SQLをserver側へ永続化しない。
10. 各小課題に段階hintと閉じた模範SQLを用意できる。
11. SQL Runner、container、timeout、cleanup、判定境界を対象testで確認できる。
12. Git編とJavaクラス設計問題集のroute、DB、Runner、公開版へ回帰がない。

## 16. Phase 2開始前に行うUI参照画像の扱い

Phase 1完了後、確定した表示情報を使ってPC向けSQL editor兼問題画面の参照画像を1枚生成する。画像は視覚方向の参考であり、画像内の誤字、架空data、pixel単位の寸法を仕様にしない。

- 推奨size: 2048×1152、16:9
- 主領域: SQL editorと実行結果
- 補助領域: 問題、table、hint、履歴、小課題一覧
- 色: deep navy、blue、cyan、white。Orangeを主色またはaccentにしない
- 禁止: 外部brand、login、広告、不要な通知、過度な装飾、読めない小文字

画像と本書が矛盾する場合は本書を正とする。
