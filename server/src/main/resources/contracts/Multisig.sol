// SPDX-License-Identifier: MIT
pragma solidity ^0.8.26;

interface IERC20 {
    function transfer(address to, uint256 amount) external returns (bool);
}

contract Multisig {
    struct Transaction {
        address token;
        address to;
        uint256 amount;
        bool executed;
        uint256 confirmations;
    }

    address[] public owners;
    mapping(address => bool) public isOwner;
    uint256 public threshold;
    Transaction[] public transactions;
    mapping(uint256 => mapping(address => bool)) public confirmedBy;

    event TransactionSubmitted(uint256 indexed txId, address token, address to, uint256 amount);
    event TransactionConfirmed(uint256 indexed txId, address indexed owner);
    event ConfirmationRevoked(uint256 indexed txId, address indexed owner);
    event TransactionExecuted(uint256 indexed txId);

    modifier onlyOwner() {
        require(isOwner[msg.sender], "Not an owner");
        _;
    }

    constructor(address[] memory _owners, uint256 _threshold) {
        require(_owners.length > 0, "No owners");
        require(_threshold > 0 && _threshold <= _owners.length, "Invalid threshold");
        for (uint256 i = 0; i < _owners.length; i++) {
            isOwner[_owners[i]] = true;
            owners.push(_owners[i]);
        }
        threshold = _threshold;
    }

    function submit(address token, address to, uint256 amount) external onlyOwner returns (uint256 txId) {
        txId = transactions.length;
        transactions.push(Transaction(token, to, amount, false, 0));
        emit TransactionSubmitted(txId, token, to, amount);
    }

    function confirm(uint256 txId) external onlyOwner {
        require(txId < transactions.length, "Invalid txId");
        require(!confirmedBy[txId][msg.sender], "Already confirmed");
        require(!transactions[txId].executed, "Already executed");
        confirmedBy[txId][msg.sender] = true;
        transactions[txId].confirmations++;
        emit TransactionConfirmed(txId, msg.sender);
    }

    function revoke(uint256 txId) external onlyOwner {
        require(txId < transactions.length, "Invalid txId");
        require(confirmedBy[txId][msg.sender], "Not confirmed");
        require(!transactions[txId].executed, "Already executed");
        confirmedBy[txId][msg.sender] = false;
        transactions[txId].confirmations--;
        emit ConfirmationRevoked(txId, msg.sender);
    }

    function execute(uint256 txId) external onlyOwner {
        require(txId < transactions.length, "Invalid txId");
        Transaction storage txn = transactions[txId];
        require(!txn.executed, "Already executed");
        require(txn.confirmations >= threshold, "Threshold not met");
        txn.executed = true;
        require(IERC20(txn.token).transfer(txn.to, txn.amount), "Transfer failed");
        emit TransactionExecuted(txId);
    }

    function getTransaction(uint256 txId) external view returns (
        address token, address to, uint256 amount, bool executed, uint256 confirmations
    ) {
        Transaction storage txn = transactions[txId];
        return (txn.token, txn.to, txn.amount, txn.executed, txn.confirmations);
    }

    function getTransactionCount() external view returns (uint256) {
        return transactions.length;
    }

    function isConfirmedBy(uint256 txId, address owner) external view returns (bool) {
        return confirmedBy[txId][owner];
    }
}
