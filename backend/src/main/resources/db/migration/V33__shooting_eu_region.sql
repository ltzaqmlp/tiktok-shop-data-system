-- EU is a shooting region; country markets remain the permission and reporting units.
INSERT INTO dim_market(market_code,market_name,currency_code,enabled)
VALUES ('EU','欧盟','EUR',false)
ON CONFLICT (market_code) DO NOTHING;
