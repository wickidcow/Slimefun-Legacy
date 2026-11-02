#!/usr/bin/env python3
"""Fail-closed checks for the disposable Doctor connected-player retirement probe."""
import unittest

from scripts.resource_pack_doctor_probe import confirmed_retirement_reset


CLIENT = (
    "Doctor client reached play: SFLDoctorBot 015cac31-2c45-3f43-86e1-6e94914a0e79\n"
    "Error: read ECONNRESET\n"
    "    at TCP.onStreamRead (node:internal/stream_base_commons:221:20)\n"
)
PROOF = "RESOURCE_PACK_DOCTOR_PROBE_PASS player-retire | 26.2-7-14b7fee (MC: 26.2)\n"
SERVER = (
    "[INFO]: SFLDoctorBot joined the game\n"
    "[INFO]: SFLDoctorBot lost connection: Doctor retirement probe\n"
    "[INFO]: SFLDoctorBot left the game\n"
    "[INFO]: [ResourcePackDoctorProbe] " + PROOF
)


class RetirementResetProofTest(unittest.TestCase):
    def assert_rejected(self, *, exit_code=1, client=CLIENT, server=SERVER, proof=PROOF):
        self.assertFalse(confirmed_retirement_reset(exit_code, client, server, proof))

    def test_exact_server_confirmed_retirement_reset_is_accepted(self):
        self.assertTrue(confirmed_retirement_reset(1, CLIENT, SERVER, PROOF))

    def test_normal_exit_is_not_a_reset(self):
        self.assert_rejected(exit_code=0)

    def test_failed_or_missing_play_login_is_not_accepted(self):
        self.assert_rejected(client="Error: read ECONNRESET\n")
        self.assert_rejected(client=CLIENT.replace("SFLDoctorBot 015", "OtherPlayer 015"))

    def test_unrelated_client_error_or_multiple_errors_stays_fatal(self):
        self.assert_rejected(client=CLIENT.replace("read ECONNRESET", "connect ECONNREFUSED"))
        self.assert_rejected(client=CLIENT + "Error: Unexpected packet\n")

    def test_other_disconnect_failure_stays_fatal(self):
        self.assert_rejected(client=CLIENT + "Unexpected client disconnect: socket closed\n")
        self.assert_rejected(client=CLIENT + "Doctor client failed\n")

    def test_wrong_kick_or_missing_leave_is_not_accepted(self):
        self.assert_rejected(server=SERVER.replace("Doctor retirement probe", "Timed out"))
        self.assert_rejected(server=SERVER.replace("SFLDoctorBot left the game", "Player never left"))

    def test_explicit_server_failure_stays_fatal(self):
        self.assert_rejected(server=SERVER + "RESOURCE_PACK_DOCTOR_PROBE_FAIL ownership\n")

    def test_retirement_proof_must_be_present_and_correct(self):
        self.assert_rejected(proof="")
        self.assert_rejected(proof=PROOF.replace("player-retire", "player-join"))
        self.assert_rejected(server=SERVER.replace(PROOF, ""))

    def test_pass_cannot_precede_server_retirement(self):
        self.assert_rejected(server=SERVER.replace(
            "SFLDoctorBot lost connection: Doctor retirement probe\n", ""
        ) + "SFLDoctorBot lost connection: Doctor retirement probe\n")

    def test_non_reset_exit_codes_are_not_forgiven(self):
        for exit_code in (2, 137, -9):
            with self.subTest(exit_code=exit_code):
                self.assert_rejected(exit_code=exit_code)

    def test_proof_file_must_match_the_exact_server_proof(self):
        self.assert_rejected(proof=PROOF.replace("26.2-7", "26.3-145"))
        self.assert_rejected(server=SERVER.replace(PROOF, PROOF.rstrip() + " corrupted\n"))

    def test_multiline_or_empty_runtime_proof_is_rejected(self):
        self.assert_rejected(proof=PROOF + "unexpected additional proof\n")
        self.assert_rejected(proof="RESOURCE_PACK_DOCTOR_PROBE_PASS player-retire | \n")

    def test_leave_must_follow_the_kick_and_precede_the_exact_proof(self):
        self.assert_rejected(server=SERVER.replace("[INFO]: SFLDoctorBot left the game\n", "")
                             + "[INFO]: SFLDoctorBot left the game\n")
        self.assert_rejected(server="[INFO]: SFLDoctorBot left the game\n"
                             + SERVER.replace("[INFO]: SFLDoctorBot left the game\n", ""))

    def test_arbitrary_client_connection_reset_without_proof_is_fatal(self):
        self.assert_rejected(server="Server socket disconnected\n")


if __name__ == "__main__":
    unittest.main()
