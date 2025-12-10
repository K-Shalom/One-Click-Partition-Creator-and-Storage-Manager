-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 28, 2025 at 10:45 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `onclick_db`
--

-- --------------------------------------------------------

--
-- Table structure for table `activity_logs`
--

CREATE TABLE `activity_logs` (
  `log_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `machine_id` int(11) NOT NULL,
  `action` varchar(100) NOT NULL,
  `log_date` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `activity_logs`
--

INSERT INTO `activity_logs` (`log_id`, `user_id`, `machine_id`, `action`, `log_date`) VALUES
(1, 1, 1, 'User logged in successfully', '2025-11-27 16:08:04'),
(2, 1, 1, 'Synchronized 4 partition(s) with database', '2025-11-27 16:08:05'),
(3, 1, 1, 'User logged out', '2025-11-27 16:09:53'),
(4, 1, 1, 'User logged in successfully', '2025-11-27 16:10:02'),
(5, 1, 1, 'Synchronized 4 partition(s) with database', '2025-11-27 16:10:03'),
(6, 1, 1, 'Rename Volume K to shalx', '2025-11-27 16:10:34'),
(7, 1, 1, 'Rename Volume executed on K', '2025-11-27 16:10:37'),
(8, 1, 1, 'User logged in successfully', '2025-11-28 09:53:50'),
(9, 1, 1, 'Synchronized 4 partition(s) with database', '2025-11-28 09:53:52'),
(10, 1, 1, 'Refreshed user table', '2025-11-28 10:02:48'),
(11, 1, 1, 'Refreshed user table', '2025-11-28 10:03:00'),
(12, 1, 1, 'Refreshed user table', '2025-11-28 10:03:04'),
(13, 1, 1, 'Refreshed user table', '2025-11-28 10:04:16'),
(14, 1, 1, 'Accessed Remote Partition feature', '2025-11-28 10:06:12'),
(15, 1, 1, 'Refreshed user table', '2025-11-28 10:06:51'),
(16, 1, 1, 'Accessed Remote Partition feature', '2025-11-28 10:06:54'),
(17, 1, 1, 'User logged in successfully', '2025-11-28 13:29:09'),
(18, 1, 1, 'Synchronized 4 partition(s) with database', '2025-11-28 13:29:12'),
(19, 1, 1, 'Refreshed user table', '2025-11-28 13:43:50'),
(20, 4, 2, 'User logged in successfully', '2025-11-28 13:44:03'),
(21, 4, 2, 'Synchronized 2 partition(s) with database', '2025-11-28 13:44:07'),
(22, 1, 1, 'Accessed Remote Partition feature', '2025-11-28 13:44:20'),
(23, 1, 1, 'Deleted user: gilbert', '2025-11-28 13:44:57'),
(24, 1, 1, 'Deleted user: shimo', '2025-11-28 13:45:01'),
(25, 1, 1, 'Accessed Remote Partition feature', '2025-11-28 13:45:03');

-- --------------------------------------------------------

--
-- Table structure for table `machines`
--

CREATE TABLE `machines` (
  `machine_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `machine_name` varchar(50) NOT NULL,
  `ip_address` varchar(15) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `machines`
--

INSERT INTO `machines` (`machine_id`, `user_id`, `machine_name`, `ip_address`) VALUES
(1, 1, 'DORCAS-PC', '172.24.180.228'),
(2, 4, 'LAPTOP-DRIFFPTS', '172.24.180.250');

-- --------------------------------------------------------

--
-- Table structure for table `partitions`
--

CREATE TABLE `partitions` (
  `partition_id` int(11) NOT NULL,
  `machine_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `drive_letter` varchar(2) NOT NULL,
  `size_gb` int(11) NOT NULL CHECK (`size_gb` > 0),
  `created_date` date NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `partitions`
--

INSERT INTO `partitions` (`partition_id`, `machine_id`, `user_id`, `drive_letter`, `size_gb`, `created_date`) VALUES
(1, 1, 1, 'C:', 279, '2025-11-27'),
(2, 1, 1, 'G:', 15, '2025-11-27'),
(3, 1, 1, 'K:', 185, '2025-11-27'),
(4, 1, 1, 'X:', 10, '2025-11-27'),
(5, 2, 4, 'C:', 474, '2025-11-28'),
(6, 2, 4, 'D:', 7, '2025-11-28');

-- --------------------------------------------------------

--
-- Table structure for table `users`
--

CREATE TABLE `users` (
  `user_id` int(11) NOT NULL,
  `username` varchar(50) NOT NULL,
  `password` varchar(100) NOT NULL,
  `role` enum('ADMIN','USER','','') NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `users`
--

INSERT INTO `users` (`user_id`, `username`, `password`, `role`) VALUES
(1, 'Shalom', 'Shalx@12345', 'ADMIN'),
(4, 'niyi', '12345', 'USER');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `activity_logs`
--
ALTER TABLE `activity_logs`
  ADD PRIMARY KEY (`log_id`),
  ADD KEY `idx_userid_logs` (`user_id`),
  ADD KEY `idx_machineid_logs` (`machine_id`);

--
-- Indexes for table `machines`
--
ALTER TABLE `machines`
  ADD PRIMARY KEY (`machine_id`),
  ADD KEY `idx_userid` (`user_id`);

--
-- Indexes for table `partitions`
--
ALTER TABLE `partitions`
  ADD PRIMARY KEY (`partition_id`),
  ADD KEY `idx_machineid` (`machine_id`),
  ADD KEY `idx_userid_partitions` (`user_id`);

--
-- Indexes for table `users`
--
ALTER TABLE `users`
  ADD PRIMARY KEY (`user_id`),
  ADD UNIQUE KEY `username` (`username`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `activity_logs`
--
ALTER TABLE `activity_logs`
  MODIFY `log_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=26;

--
-- AUTO_INCREMENT for table `machines`
--
ALTER TABLE `machines`
  MODIFY `machine_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT for table `partitions`
--
ALTER TABLE `partitions`
  MODIFY `partition_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

--
-- AUTO_INCREMENT for table `users`
--
ALTER TABLE `users`
  MODIFY `user_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `activity_logs`
--
ALTER TABLE `activity_logs`
  ADD CONSTRAINT `activity_logs_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `activity_logs_ibfk_2` FOREIGN KEY (`machine_id`) REFERENCES `machines` (`machine_id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `machines`
--
ALTER TABLE `machines`
  ADD CONSTRAINT `machines_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `partitions`
--
ALTER TABLE `partitions`
  ADD CONSTRAINT `partitions_ibfk_1` FOREIGN KEY (`machine_id`) REFERENCES `machines` (`machine_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `partitions_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
