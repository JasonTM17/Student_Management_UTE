-- V37: Align thesis topic status for catalog visibility
-- Topics seeded or submitted as APPROVED are published for student enrollment in open rounds.

UPDATE thesis.thesis_topic
SET status = 'PUBLISHED'
WHERE status = 'APPROVED';
