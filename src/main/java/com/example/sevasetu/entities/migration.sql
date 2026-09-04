-- ============================================================================
-- SEVASETU DATABASE MIGRATION SCRIPT
-- State Focus: Karnataka (Scalable to Pan-India)
-- ============================================================================

BEGIN;

-- 1. Enable UUID Extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";



-- ============================================================================
-- CLEANUP / TEARDOWN (For re-running migration safely)
-- ============================================================================
DROP TABLE IF EXISTS user_applications CASCADE;
DROP TABLE IF EXISTS scheme_documents CASCADE;
DROP TABLE IF EXISTS scheme_rules CASCADE;
DROP TABLE IF EXISTS schemes CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS districts CASCADE;
DROP TABLE IF EXISTS states CASCADE;

DROP TYPE IF EXISTS application_status CASCADE;
DROP TYPE IF EXISTS ration_card_type CASCADE;
DROP TYPE IF EXISTS gender_type CASCADE;
DROP TYPE IF EXISTS scheme_category CASCADE;

-- ============================================================================
-- CUSTOM TYPES & ENUMS
-- ============================================================================
CREATE TYPE gender_type AS ENUM ('MALE', 'FEMALE', 'TRANSGENDER', 'ALL');
CREATE TYPE ration_card_type AS ENUM ('BPL', 'APL', 'AAY', 'NONE');
CREATE TYPE application_status AS ENUM ('BOOKMARKED', 'APPLIED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED');
CREATE TYPE scheme_category AS ENUM (
    'HEALTHCARE',
    'WOMEN_WELFARE',
    'EDUCATION',
    'AGRICULTURE',
    'UNEMPLOYMENT',
    'HOUSING',
    'FINANCIAL_ASSISTANCE'
);

-- ============================================================================
-- 1. STATES
-- ============================================================================
CREATE TABLE states (
    id SERIAL PRIMARY KEY,
    code VARCHAR(5) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 2. DISTRICTS
-- ============================================================================
CREATE TABLE districts (
    id SERIAL PRIMARY KEY,
    state_id INT NOT NULL REFERENCES states(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(state_id, name)
);

-- ============================================================================
-- 3. CITIZEN USERS & DEMOGRAPHICS
-- ============================================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number VARCHAR(15) UNIQUE NOT NULL,
    full_name VARCHAR(150),
    age INT CHECK (age >= 0 AND age <= 125),
    gender gender_type DEFAULT 'ALL',
    caste_category VARCHAR(50) CHECK (
        caste_category IN ('SC', 'ST', 'CAT_1', '2A', '2B', '3A', '3B', 'GENERAL')
    ),
    annual_income NUMERIC(12, 2) DEFAULT 0.00,
    ration_card ration_card_type DEFAULT 'NONE',
    state_id INT REFERENCES states(id) ON DELETE SET NULL,
    district_id INT REFERENCES districts(id) ON DELETE SET NULL,
    is_state_domicile BOOLEAN DEFAULT TRUE,
    is_differently_abled BOOLEAN DEFAULT FALSE,
    occupation VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 4. SCHEMES METADATA
-- ============================================================================
CREATE TABLE schemes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    slug VARCHAR(150) UNIQUE NOT NULL,
    state_id INT REFERENCES states(id) ON DELETE SET NULL, -- NULL indicates Central/National scheme
    category scheme_category NOT NULL,
    title_en VARCHAR(255) NOT NULL,
    title_local VARCHAR(255),
    description_en TEXT NOT NULL,
    description_local TEXT,
    benefits_summary TEXT NOT NULL,
    application_url VARCHAR(500),
    offline_submission_desk VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 5. SCHEME ELIGIBILITY RULES
-- ============================================================================
CREATE TABLE scheme_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    scheme_id UUID UNIQUE NOT NULL REFERENCES schemes(id) ON DELETE CASCADE,
    min_age INT DEFAULT 0 CHECK (min_age >= 0),
    max_age INT DEFAULT 125 CHECK (max_age <= 125),
    allowed_genders gender_type[] DEFAULT '{ALL}',
    allowed_castes TEXT[] DEFAULT '{ALL}',
    max_annual_income NUMERIC(12, 2), -- NULL means no income ceiling
    allowed_ration_cards ration_card_type[] DEFAULT '{BPL, APL, AAY, NONE}',
    requires_state_domicile BOOLEAN DEFAULT FALSE,
    differently_abled_only BOOLEAN DEFAULT FALSE,
    specific_occupations TEXT[] DEFAULT '{ALL}',
    custom_criteria JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 6. SCHEME REQUIRED DOCUMENTS
-- ============================================================================
CREATE TABLE scheme_documents (
    id SERIAL PRIMARY KEY,
    scheme_id UUID NOT NULL REFERENCES schemes(id) ON DELETE CASCADE,
    document_code VARCHAR(100) NOT NULL,
    document_name_en VARCHAR(200) NOT NULL,
    document_name_local VARCHAR(200),
    is_mandatory BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 7. USER APPLICATIONS & BOOKMARKS
-- ============================================================================
CREATE TABLE user_applications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scheme_id UUID NOT NULL REFERENCES schemes(id) ON DELETE CASCADE,
    status application_status DEFAULT 'BOOKMARKED',
    application_reference_no VARCHAR(100),
    applied_date DATE,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, scheme_id)
);

-- ============================================================================
-- PERFORMANCE & FILTERING INDEXES
-- ============================================================================
CREATE INDEX idx_schemes_state ON schemes(state_id);
CREATE INDEX idx_schemes_category ON schemes(category);
CREATE INDEX idx_schemes_active ON schemes(is_active);

CREATE INDEX idx_scheme_rules_age ON scheme_rules(min_age, max_age);
CREATE INDEX idx_scheme_rules_income ON scheme_rules(max_annual_income);
CREATE INDEX idx_scheme_rules_genders ON scheme_rules USING GIN(allowed_genders);
CREATE INDEX idx_scheme_rules_castes ON scheme_rules USING GIN(allowed_castes);
CREATE INDEX idx_scheme_rules_ration ON scheme_rules USING GIN(allowed_ration_cards);
CREATE INDEX idx_scheme_rules_custom ON scheme_rules USING GIN(custom_criteria);

CREATE INDEX idx_user_applications_user ON user_applications(user_id);
CREATE INDEX idx_user_applications_status ON user_applications(status);

-- ============================================================================
-- INITIAL SEED DATA: KARNATAKA
-- ============================================================================

-- 1. Insert State
INSERT INTO states (code, name) VALUES ('KA', 'Karnataka');

-- 2. Insert Core Karnataka Districts
INSERT INTO districts (state_id, name)
VALUES 
    ((SELECT id FROM states WHERE code = 'KA'), 'Bengaluru Urban'),
    ((SELECT id FROM states WHERE code = 'KA'), 'Bengaluru Rural'),
    ((SELECT id FROM states WHERE code = 'KA'), 'Mysuru'),
    ((SELECT id FROM states WHERE code = 'KA'), 'Belagavi'),
    ((SELECT id FROM states WHERE code = 'KA'), 'Kalaburagi'),
    ((SELECT id FROM states WHERE code = 'KA'), 'Dakshina Kannada'),
    ((SELECT id FROM states WHERE code = 'KA'), 'Dharwad'),
    ((SELECT id FROM states WHERE code = 'KA'), 'Tumakuru');

-- 3. Seed "Gruha Lakshmi" Scheme
INSERT INTO schemes (
    slug,
    state_id,
    category,
    title_en,
    title_local,
    description_en,
    description_local,
    benefits_summary,
    application_url,
    offline_submission_desk
) VALUES (
    'gruha-lakshmi',
    (SELECT id FROM states WHERE code = 'KA'),
    'WOMEN_WELFARE',
    'Gruha Lakshmi Scheme',
    'ಗೃಹ ಲಕ್ಷ್ಮಿ ಯೋಜನೆ',
    'Financial assistance of ₹2,000 per month provided directly to the woman head of eligible households in Karnataka.',
    'ಕರ್ನಾಟಕದ ಅರ್ಹ ಕುಟುಂಬಗಳ ಮಹಿಳಾ ಮುಖ್ಯಸ್ಥರಿಗೆ ಪ್ರತಿ ತಿಂಗಳು ₹2,000 ನೇರ ಆರ್ಥಿಕ ನೆರವು.',
    '₹2,000 per month DBT (Direct Benefit Transfer)',
    'https://sevasindhu.karnataka.gov.in',
    'Gram One, Bangalore One, Karnataka One, Bapuji Seva Kendra counters'
);

-- 4. Seed Eligibility Rules for Gruha Lakshmi
INSERT INTO scheme_rules (
    scheme_id,
    min_age,
    max_age,
    allowed_genders,
    allowed_castes,
    allowed_ration_cards,
    requires_state_domicile
) VALUES (
    (SELECT id FROM schemes WHERE slug = 'gruha-lakshmi'),
    18,
    125,
    ARRAY['FEMALE'::gender_type],
    ARRAY['ALL'],
    ARRAY['BPL'::ration_card_type, 'AAY'::ration_card_type, 'APL'::ration_card_type],
    TRUE
);

-- 5. Seed Mandatory Documents for Gruha Lakshmi
INSERT INTO scheme_documents (scheme_id, document_code, document_name_en, document_name_local, is_mandatory)
VALUES 
    ((SELECT id FROM schemes WHERE slug = 'gruha-lakshmi'), 'AADHAAR', 'Aadhaar Card of Applicant & Spouse', 'ಅರ್ಜಿದಾರರು ಮತ್ತು ಪತಿಯ ಆಧಾರ್ ಕಾರ್ಡ್', TRUE),
    ((SELECT id FROM schemes WHERE slug = 'gruha-lakshmi'), 'RATION_CARD', 'Ration Card (BPL/APL/AAY)', 'ಪಡಿತರ ಚೀಟಿ (BPL/APL/AAY)', TRUE),
    ((SELECT id FROM schemes WHERE slug = 'gruha-lakshmi'), 'BANK_PASSBOOK', 'Aadhaar-Linked Bank Account Passbook', 'ಆಧಾರ್ ಲಿಂಕ್ ಮಾಡಲಾದ ಬ್ಯಾಂಕ್ ಪಾಸ್‌ಬುಕ್', TRUE);



CREATE TABLE user_otps (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number VARCHAR(15) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL, -- SHA-256 / bcrypt hashed 6-digit OTP
    attempts INT DEFAULT 0 CHECK (attempts <= 5),
    is_consumed BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Fast lookup index for active verification
CREATE INDEX idx_user_otps_lookup 
ON user_otps (phone_number, is_consumed, expires_at);

COMMIT;