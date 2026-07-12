// SPDX-License-Identifier: MIT
pragma solidity ^0.8.26;

contract DepToken {
    string public constant name = "Dep Token";
    string public constant symbol = "DEP";
    uint8 public constant decimals = 2;
    uint256 public constant totalSupply = 10_000_000_000;
    uint256 private constant INITIAL_ALLOCATION = totalSupply / 2;

    mapping(address => uint256) private balances;
    mapping(address => mapping(address => uint256)) private allowances;

    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);

    constructor(address initialHolderA, address initialHolderB) {
        balances[initialHolderA] = INITIAL_ALLOCATION;
        balances[initialHolderB] = INITIAL_ALLOCATION;
        emit Transfer(address(0), initialHolderA, INITIAL_ALLOCATION);
        emit Transfer(address(0), initialHolderB, INITIAL_ALLOCATION);
    }

    function balanceOf(address account) external view returns (uint256) {
        return balances[account];
    }

    function allowance(address owner, address spender) external view returns (uint256) {
        return allowances[owner][spender];
    }

    function transfer(address to, uint256 amount) external returns (bool) {
        require(balances[msg.sender] >= amount, "Insufficient balance");
        balances[msg.sender] -= amount;
        balances[to] += amount;
        emit Transfer(msg.sender, to, amount);
        return true;
    }

    function approve(address spender, uint256 amount) external returns (bool) {
        allowances[msg.sender][spender] = amount;
        emit Approval(msg.sender, spender, amount);
        return true;
    }

    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        require(balances[from] >= amount, "Insufficient balance");
        require(allowances[from][msg.sender] >= amount, "Insufficient allowance");
        balances[from] -= amount;
        balances[to] += amount;
        allowances[from][msg.sender] -= amount;
        emit Transfer(from, to, amount);
        return true;
    }
}
