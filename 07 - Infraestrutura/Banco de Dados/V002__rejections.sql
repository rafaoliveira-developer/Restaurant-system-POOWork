CREATE TABLE order_rejections (
  employee_id bigint NOT NULL REFERENCES employees,
  order_id uuid NOT NULL,
  error varchar(300) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (employee_id, order_id)
);
