// SPDX-License-Identifier: MIT
pragma solidity ^0.8.26;

contract PriceOracle {
    struct PriceFeed {
        uint256 price;
        uint256 timestamp;
    }

    address public admin;
    mapping(bytes32 => PriceFeed) private feeds;
    mapping(address => bool) public reporters;

    event PriceUpdated(bytes32 indexed key, uint256 price, uint256 timestamp);
    event ReporterAdded(address indexed reporter);
    event ReporterRemoved(address indexed reporter);

    constructor(address _admin) {
        admin = _admin;
        reporters[_admin] = true;
    }

    modifier onlyReporter() {
        require(reporters[msg.sender], "Not authorized");
        _;
    }

    modifier onlyAdmin() {
        require(msg.sender == admin, "Not admin");
        _;
    }

    function updatePrice(bytes32 key, uint256 price, uint256 timestamp) external onlyReporter {
        feeds[key] = PriceFeed(price, timestamp);
        emit PriceUpdated(key, price, timestamp);
    }

    function getPrice(bytes32 key) external view returns (uint256 price, uint256 timestamp) {
        PriceFeed memory feed = feeds[key];
        return (feed.price, feed.timestamp);
    }

    function addReporter(address reporter) external onlyAdmin {
        reporters[reporter] = true;
        emit ReporterAdded(reporter);
    }

    function removeReporter(address reporter) external onlyAdmin {
        reporters[reporter] = false;
        emit ReporterRemoved(reporter);
    }

    function isReporter(address account) external view returns (bool) {
        return reporters[account];
    }
}
