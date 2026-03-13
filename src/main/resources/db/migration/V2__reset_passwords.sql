-- Migration V2 : Reset des mots de passe par defaut
-- admin123 encode en BCrypt cost 10
UPDATE users SET password = '$2b$10$Ea0S1JsCQVeLVT8CpD/np.vfCE7b3exg.adqQDxl6s4izYeDT3HwG' WHERE username = 'admin';
UPDATE users SET password = '$2b$10$Ea0S1JsCQVeLVT8CpD/np.vfCE7b3exg.adqQDxl6s4izYeDT3HwG' WHERE username = 'agent';
