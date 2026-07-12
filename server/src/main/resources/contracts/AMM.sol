// SPDX-License-Identifier: MIT
pragma solidity ^0.8.26;

interface IERC20 {
    function transferFrom(address from, address to, uint256 amount) external returns (bool);
    function transfer(address to, uint256 amount) external returns (bool);
}

contract AMM {
    address public token0;
    address public token1;
    uint256 public reserve0;
    uint256 public reserve1;
    uint256 public totalLiquidity;
    mapping(address => uint256) public liquidity;

    constructor(address _token0, address _token1) {
        token0 = _token0;
        token1 = _token1;
    }

    function addLiquidity(uint256 amount0, uint256 amount1) external returns (uint256 lp) {
        require(amount0 > 0 && amount1 > 0, "Amounts must be positive");
        require(IERC20(token0).transferFrom(msg.sender, address(this), amount0), "token0 transfer failed");
        require(IERC20(token1).transferFrom(msg.sender, address(this), amount1), "token1 transfer failed");

        if (totalLiquidity == 0) {
            lp = sqrt(amount0 * amount1);
        } else {
            lp = min(
                amount0 * totalLiquidity / reserve0,
                amount1 * totalLiquidity / reserve1
            );
        }
        require(lp > 0, "Insufficient liquidity minted");

        liquidity[msg.sender] += lp;
        totalLiquidity += lp;
        reserve0 += amount0;
        reserve1 += amount1;
    }

    function removeLiquidity(uint256 lpAmount) external returns (uint256 amount0, uint256 amount1) {
        require(liquidity[msg.sender] >= lpAmount, "Insufficient LP balance");
        require(totalLiquidity > 0, "No liquidity");

        amount0 = lpAmount * reserve0 / totalLiquidity;
        amount1 = lpAmount * reserve1 / totalLiquidity;

        liquidity[msg.sender] -= lpAmount;
        totalLiquidity -= lpAmount;
        reserve0 -= amount0;
        reserve1 -= amount1;

        require(IERC20(token0).transfer(msg.sender, amount0), "token0 transfer failed");
        require(IERC20(token1).transfer(msg.sender, amount1), "token1 transfer failed");
    }

    function swap(address tokenIn, uint256 amountIn, uint256 minAmountOut) external returns (uint256 amountOut) {
        require(tokenIn == token0 || tokenIn == token1, "Invalid token");
        require(amountIn > 0, "Amount must be positive");

        bool isToken0 = tokenIn == token0;
        (uint256 reserveIn, uint256 reserveOut) = isToken0 ? (reserve0, reserve1) : (reserve1, reserve0);
        address tokenOut = isToken0 ? token1 : token0;

        require(IERC20(tokenIn).transferFrom(msg.sender, address(this), amountIn), "transfer failed");

        uint256 amountInWithFee = amountIn * 997;
        amountOut = amountInWithFee * reserveOut / (reserveIn * 1000 + amountInWithFee);
        require(amountOut >= minAmountOut, "Insufficient output amount");

        if (isToken0) {
            reserve0 += amountIn;
            reserve1 -= amountOut;
        } else {
            reserve1 += amountIn;
            reserve0 -= amountOut;
        }

        require(IERC20(tokenOut).transfer(msg.sender, amountOut), "transfer failed");
    }

    function getReserves() external view returns (uint256, uint256) {
        return (reserve0, reserve1);
    }

    function getLiquidityOf(address provider) external view returns (uint256) {
        return liquidity[provider];
    }

    function sqrt(uint256 y) internal pure returns (uint256 z) {
        if (y > 3) {
            z = y;
            uint256 x = y / 2 + 1;
            while (x < z) {
                z = x;
                x = (y / x + x) / 2;
            }
        } else if (y != 0) {
            z = 1;
        }
    }

    function min(uint256 a, uint256 b) internal pure returns (uint256) {
        return a < b ? a : b;
    }
}
