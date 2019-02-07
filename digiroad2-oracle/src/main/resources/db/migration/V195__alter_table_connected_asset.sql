ALTER TABLE	CONNECTED_ASSET
ADD (created_date DATE DEFAULT SYSDATE NOT NULL,
		modified_date DATE,
		valid_to DATE);

ALTER TABLE CONNECTED_ASSET RENAME COLUMN asset_id TO linear_asset_id;
ALTER TABLE CONNECTED_ASSET RENAME COLUMN connected_asset_id TO point_asset_id;