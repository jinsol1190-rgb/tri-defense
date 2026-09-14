// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {ThreatRegistry} from "../src/ThreatRegistry.sol";
import {DeployLocal} from "../script/DeployLocal.s.sol";
import {VerifierAdapter} from "../src/VerifierAdapter.sol";
import {MockVerifier} from "../src/mocks/MockVerifier.sol";
import {Vm} from "./ThreatRegistry.t.sol";

// MOCK malformed target for checking untrusted ABI responses.
contract ResponseFixture {
    uint256 private mode;

    function setMode(uint256 value) external {
        mode = value;
    }

    fallback() external {
        uint256 value = mode;
        assembly {
            if eq(value, 6) { sstore(0, 0) }
            switch value
            case 0 { revert(0, 0) }
            case 1 { return(0, 0) }
            case 2 {
                mstore(0, 2)
                return(0, 32)
            }
            case 3 {
                mstore(0, 1)
                return(0, 64)
            }
            case 4 {
                mstore(0, 0)
                return(0, 32)
            }
            default {
                mstore(0, 1)
                return(0, 32)
            }
        }
    }
}

contract VerifierAdapterTest {
    Vm private constant VM = Vm(address(uint160(uint256(keccak256("hevm cheat code")))));

    function testAdapterRejectsRevertEmptyMalformedAndFalseResponses() public {
        ResponseFixture target = new ResponseFixture();
        VerifierAdapter adapter = new VerifierAdapter(address(target), 1);
        uint256[] memory p = new uint256[](8);
        p[0] = 1;
        for (uint256 i = 0; i <= 4; ++i) {
            target.setMode(i);
            require(!adapter.verifyProof(bytes("MOCK"), p), "must fail closed");
        }
        target.setMode(6);
        require(!adapter.verifyProof{gas: 100_000}(bytes("MOCK"), p), "static call must forbid state writes");
        target.setMode(5);
        require(adapter.verifyProof(bytes("MOCK"), p), "canonical true");
    }

    function testRegistryRejectsCrossChainContextEvenWhenTargetAccepts() public {
        VM.chainId(31337);
        ResponseFixture target = new ResponseFixture();
        target.setMode(5); // MOCK adversarial target, deliberately accepts canonical responses.
        VerifierAdapter adapter = new VerifierAdapter(address(target), 1);
        ThreatRegistry registry =
            new ThreatRegistry(adapter, 7000, address(this), bytes32(uint256(1)), bytes32(uint256(2)));
        uint256[] memory p = new uint256[](8);
        p[0] = 1;
        p[1] = 9000;
        p[3] = 42;
        p[4] = 31337;
        p[5] = uint160(address(registry));
        p[6] = uint160(address(this));
        p[7] = 1;
        VM.chainId(31338);
        VM.recordLogs();
        VM.expectRevert(ThreatRegistry.PublicInputMismatch.selector);
        registry.registerThreat(bytes32(uint256(42)), 9000, 1, bytes("MOCK"), p);
        require(VM.getRecordedLogs().length == 0, "cross-chain failure logs");
        bytes32 id = keccak256(abi.encode(uint256(31338), address(registry), address(this), uint256(1)));
        require(!registry.exists(id) && !registry.usedSubmission(id), "cross-chain state");
    }

    function testDeploymentScriptRefusesPublicNetwork() public {
        DeployLocal script = new DeployLocal();
        VM.chainId(84532);
        VM.expectRevert(bytes("MOCK deployment is local-only"));
        script.run(address(this));
    }

    function testAdapterRejectsInvalidConfiguration() public {
        VM.expectRevert(VerifierAdapter.InvalidConfiguration.selector);
        new VerifierAdapter(address(0), 1);
        ResponseFixture target = new ResponseFixture();
        VM.expectRevert(VerifierAdapter.InvalidConfiguration.selector);
        new VerifierAdapter(address(target), 0);
    }

    function testMockIsLocalOnly() public {
        VM.chainId(84532);
        VM.expectRevert(MockVerifier.LocalOnly.selector);
        new MockVerifier(address(this));
    }

    function testMockDefaultsToRejectionAndFixtureCanBeRevoked() public {
        VM.chainId(31337);
        MockVerifier verifier = new MockVerifier(address(this));
        uint256[] memory p = new uint256[](8);
        bytes memory proof = bytes("MOCK_PROOF");
        require(!verifier.verifyProof(proof, p), "unconfigured fixture");
        verifier.setFixture(proof, p, true);
        require(verifier.verifyProof(proof, p), "approved fixture");
        verifier.setFixture(proof, p, false);
        require(!verifier.verifyProof(proof, p), "revoked fixture");
        VM.prank(address(0xBEEF));
        VM.expectRevert(MockVerifier.Unauthorized.selector);
        verifier.setFixture(proof, p, true);
        VM.expectRevert(MockVerifier.InvalidFixture.selector);
        verifier.setFixture(bytes(""), p, true);
        VM.expectRevert(MockVerifier.InvalidFixture.selector);
        new MockVerifier(address(0));
    }
}
