// SPDX-License-Identifier: MIT
pragma solidity ^0.8.26;

contract ISTCoin {
    string public constant name = "IST Coin";
    string public constant symbol = "IST";
    uint8 public constant decimals = 2;
    uint256 public constant totalSupply = 10_000_000_000; // 100M * 100 (decimals=2)

    mapping(address => uint256) private balances;
    mapping(address => mapping(address => uint256)) private allowances;

    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);

    constructor(address initialHolder) {
        balances[initialHolder] = totalSupply;
        emit Transfer(address(0), initialHolder, totalSupply);
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

    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        require(balances[from] >= amount, "Insufficient balance");
        require(allowances[from][msg.sender] >= amount, "Insufficient allowance");

        balances[from] -= amount;
        balances[to] += amount;
        allowances[from][msg.sender] -= amount;

        emit Transfer(from, to, amount);
        return true;
    }

    function approve(address spender, uint256 newValue, uint256 expectedCurrentValue)
        external returns (bool) {
        require(
            allowances[msg.sender][spender] == expectedCurrentValue,
            "Current allowance mismatch"
        );

        allowances[msg.sender][spender] = newValue;
        emit Approval(msg.sender, spender, newValue);
        return true;
    }

    function increaseAllowance(address spender, uint256 addedValue) external returns (bool) {
        allowances[msg.sender][spender] += addedValue;
        emit Approval(msg.sender, spender, allowances[msg.sender][spender]);
        return true;
    }

    function decreaseAllowance(address spender, uint256 subtractedValue) external returns (bool) {
        require(allowances[msg.sender][spender] >= subtractedValue, "Insufficient allowance");
        allowances[msg.sender][spender] -= subtractedValue;
        emit Approval(msg.sender, spender, allowances[msg.sender][spender]);
        return true;
    }
}
