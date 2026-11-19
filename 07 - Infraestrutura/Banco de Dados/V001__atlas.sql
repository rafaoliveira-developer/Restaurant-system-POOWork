CREATE TABLE employees (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username varchar(48) UNIQUE NOT NULL CHECK (username ~ '^[a-z0-9_-]+$'),
  name varchar(120) NOT NULL,
  role varchar(12) NOT NULL CHECK (role IN ('GERENTE','GARCOM','CAIXA','BAR','COZINHA')),
  schedule varchar(160) NOT NULL DEFAULT '',
  password_hash text NOT NULL,
  active boolean NOT NULL DEFAULT true
);
CREATE TABLE sessions (
  token_hash char(64) PRIMARY KEY,
  employee_id bigint NOT NULL REFERENCES employees,
  expires_at timestamptz NOT NULL
);
CREATE INDEX sessions_employee ON sessions(employee_id);
CREATE TABLE dining_tables (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  label varchar(60) UNIQUE NOT NULL,
  area varchar(60) NOT NULL,
  available boolean NOT NULL DEFAULT true
);
CREATE TABLE categories (
  name varchar(60) PRIMARY KEY
);
CREATE TABLE stations (
  code varchar(12) PRIMARY KEY
);
INSERT INTO stations VALUES ('BAR'), ('COZINHA');
CREATE TABLE products (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name varchar(120) NOT NULL,
  description varchar(600) NOT NULL DEFAULT '',
  price numeric(12,2) NOT NULL CHECK (price > 0),
  category varchar(60) NOT NULL REFERENCES categories,
  station varchar(12) NOT NULL REFERENCES stations,
  image varchar(1000) NOT NULL DEFAULT '',
  available boolean NOT NULL DEFAULT true
);
CREATE INDEX product_category ON products(category);
CREATE TABLE tabs (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  table_id bigint NOT NULL REFERENCES dining_tables,
  employee_id bigint NOT NULL REFERENCES employees,
  status varchar(16) NOT NULL DEFAULT 'ABERTA' CHECK (status IN ('ABERTA','CONTA_SOLICITADA','FECHADA','CANCELADA')),
  opened_at timestamptz NOT NULL DEFAULT now(),
  requested_at timestamptz,
  closed_at timestamptz
);
CREATE UNIQUE INDEX one_active_tab ON tabs(table_id) WHERE status IN ('ABERTA','CONTA_SOLICITADA');
CREATE TABLE orders (
  id uuid PRIMARY KEY,
  sequence bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
  idempotency_key uuid NOT NULL,
  request_hash char(64) NOT NULL,
  employee_id bigint NOT NULL REFERENCES employees,
  tab_id bigint NOT NULL REFERENCES tabs,
  created_at timestamptz NOT NULL DEFAULT now(),
  status varchar(16) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','CONFIRMADO','PARCIAL','ERRO')),
  code char(2),
  delivered_at timestamptz,
  UNIQUE (employee_id, idempotency_key)
);
CREATE INDEX orders_tab ON orders(tab_id);
CREATE INDEX orders_created ON orders(created_at DESC);
CREATE TABLE order_items (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id uuid NOT NULL REFERENCES orders,
  product_id bigint NOT NULL REFERENCES products,
  name varchar(120) NOT NULL,
  category varchar(60) NOT NULL,
  station varchar(12) NOT NULL REFERENCES stations,
  unit_price numeric(12,2) NOT NULL,
  quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 99),
  notes varchar(300) NOT NULL DEFAULT ''
);
CREATE INDEX items_order ON order_items(order_id);
CREATE TABLE dispatches (
  order_id uuid NOT NULL REFERENCES orders,
  station varchar(12) NOT NULL REFERENCES stations,
  status varchar(12) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','PREPARANDO','OK','ERRO')),
  result_by bigint REFERENCES employees,
  result_at timestamptz,
  error varchar(300) NOT NULL DEFAULT '',
  PRIMARY KEY(order_id, station)
);
CREATE INDEX dispatch_queue ON dispatches(station,status);
CREATE TABLE payments (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  tab_id bigint NOT NULL UNIQUE REFERENCES tabs,
  employee_id bigint NOT NULL REFERENCES employees,
  method varchar(12) NOT NULL CHECK (method IN ('PIX','DINHEIRO','CARTAO')),
  amount numeric(14,2) NOT NULL CHECK(amount >= 0),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE cash_closings (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  employee_id bigint NOT NULL REFERENCES employees,
  amount numeric(14,2) NOT NULL,
  payment_count bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE payments ADD COLUMN cash_closing_id bigint REFERENCES cash_closings;
CREATE TABLE print_jobs (
  id uuid PRIMARY KEY,
  order_id uuid NOT NULL REFERENCES orders,
  parent_id uuid REFERENCES print_jobs,
  content text NOT NULL,
  status varchar(12) NOT NULL DEFAULT 'PENDENTE' CHECK(status IN ('PENDENTE','IMPRIMINDO','IMPRESSO','FALHA','INCERTO','CANCELADO')),
  attempts integer NOT NULL DEFAULT 0,
  claim_id uuid,
  claimed_by bigint REFERENCES employees,
  claimed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  error varchar(300) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX print_original ON print_jobs(order_id) WHERE parent_id IS NULL;
CREATE INDEX print_queue ON print_jobs(status,created_at);
CREATE TABLE audit_events (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor bigint REFERENCES employees,
  operation varchar(60) NOT NULL,
  reference text NOT NULL,
  details text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE outbox (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  topic text NOT NULL,
  payload text NOT NULL,
  published_at timestamptz
);
CREATE INDEX outbox_pending ON outbox(id) WHERE published_at IS NULL;
CREATE TABLE operational_settings (
  key varchar(48) PRIMARY KEY,
  value varchar(300) NOT NULL
);
INSERT INTO operational_settings VALUES ('restaurant', 'ATLAS');
