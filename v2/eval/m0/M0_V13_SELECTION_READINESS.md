# M0 v1.3 selection readiness

**Status:** P0 tooling ready; official Beacon retrieval and selection not run

This layer closes the three pre-selection gaps without authorizing a responder
run:

1. official exclusion ledgers are deterministically rebuilt from the immutable
   v1.2 corpus;
2. Beacon evidence preserves the exact pulse and previous-pulse responses,
   binds the public-freeze boundary, verifies the chain link, certificate
   identity, RSA PKCS#1 v1.5/SHA-512 signature, and output hash; and
3. official selection loads only repository-fixed public-freeze, pool, registry,
   inventory, audit, and exclusion paths. There is no caller-supplied timestamp
   or seed option.

The implementation follows the deployed NIST Beacon 2.0 XSD serialization
(four-byte length prefixes) and pins current chain 2. NIST documents that chain
2 began on 2018-07-23; the stale chain-1 constant could not yield a 2026 pulse.

References:

- <https://csrc.nist.gov/projects/interoperable-randomness-beacons/beacon-20>
- <https://csrc.nist.gov/csrc/media/Projects/interoperable-randomness-beacons/documents/certificate/beacon-2.0.xsd>
- <https://doi.org/10.6028/NIST.IR.8213-draft>

## Commands

Do not run either command until the corresponding authorization is recorded.
The first command performs real NIST HTTPS requests and writes create-only
evidence:

```bash
cd v2
uv run python scripts/check_m0_v13_conformance.py fetch-beacon \
  --evidence-output eval/m0/beacon/M0_V13_BEACON_EVIDENCE.json \
  --verified-output eval/m0/beacon/M0_V13_VERIFIED_BEACON.json
```

After the evidence is independently reviewed, selection re-fetches the pinned
certificate from its exact NIST URL and re-verifies the raw evidence:

```bash
cd v2
uv run python scripts/check_m0_v13_conformance.py select \
  --beacon-evidence eval/m0/beacon/M0_V13_BEACON_EVIDENCE.json \
  --beacon-artifact eval/m0/beacon/M0_V13_VERIFIED_BEACON.json \
  --output eval/m0/manifests/M0_V13_SELECTION.json \
  --public-output eval/m0/manifests/M0_V13_SELECTION_PUBLIC.json
```

Neither command calls a model, runs G0, or implements M1. Selection outputs are
also create-only.
