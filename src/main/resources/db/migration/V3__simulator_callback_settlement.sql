ALTER TABLE simulated_charge ADD COLUMN outcome_override varchar(10) CHECK(outcome_override IN ('SUCCESS','FAILURE'));
