// SPDX-License-Identifier: MIT
pragma solidity ^0.8.26;

contract SlashingContract {
    mapping(uint256 => uint256) private stakes;
    mapping(uint256 => bool) private slashedStatus;

    function deposit(uint256 validatorId, uint256 amount) external {
        stakes[validatorId] += amount;
    }

    function slash(
        uint256 validatorId,
        uint256 viewA, bytes32 blockHashA, bytes calldata sigA,
        uint256 viewB, bytes32 blockHashB, bytes calldata sigB
    ) external {
        require(!slashedStatus[validatorId], "already slashed");
        require(sigA.length == 96 && sigB.length == 96, "invalid signature length");
        require(viewA == viewB, "views must match");
        require(blockHashA != blockHashB, "block hashes must differ");
        slashedStatus[validatorId] = true;
        stakes[validatorId] = 0;
    }

    function getStake(uint256 validatorId) external view returns (uint256) {
        return stakes[validatorId];
    }

    function isSlashed(uint256 validatorId) external view returns (bool) {
        return slashedStatus[validatorId];
    }
}
