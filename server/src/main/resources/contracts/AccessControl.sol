// SPDX-License-Identifier: MIT
pragma solidity ^0.8.26;

contract AccessControl {
    address public owner;
    mapping(address => bool) private allowed;

    event AllowedStatusChanged(address indexed account, bool allowed);

    modifier onlyOwner() {
        require(msg.sender == owner, "Only owner can call this");
        _;
    }

    constructor(address[] memory initialAllowed) {
        owner = msg.sender;
        for (uint i = 0; i < initialAllowed.length; i++) {
            allowed[initialAllowed[i]] = true;
            emit AllowedStatusChanged(initialAllowed[i], true);
        }
    }

    function canTransfer(address account) external view returns (bool) {
        return allowed[account];
    }

    function setAllowed(address account, bool _allowed) external onlyOwner {
        allowed[account] = _allowed;
        emit AllowedStatusChanged(account, _allowed);
    }
}
