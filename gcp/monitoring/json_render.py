#!/usr/bin/env python3
"""Single JSON helper for gcp/monitoring/apply.sh (STR-24).

Replaces the old mix of sed templating and inline `python3 -c` one-liners so
all JSON loading, placeholder substitution, and policy/channel lookup live in
one tested place. No shell escaping is involved anywhere: values travel
through argv into parsed JSON, never through sed.

Subcommands:

  substitute <template> <out> KEY=VALUE ...
      Copy a JSON template, replacing every "{{KEY}}" occurrence inside string
      values (recursively) with VALUE.

  set-channel <policy-template> <out> <channel-name>
      Copy an alert-policy template, replacing its notificationChannels with
      [<channel-name>].

  display-name <policy-file>
      Print the policy's displayName (used to match existing policies for
      idempotent create-or-update).

  find-policy <policies-json> <display-name>
      Print the resource name of the policy with <display-name> in a
      `gcloud monitoring policies list --format=json` dump, or "" when absent.

  find-channel <channels-json> <email>
      Print the resource name of the email notification channel for <email>
      in a `gcloud beta monitoring channels list --format=json` dump, or ""
      when absent.
"""

import json
import sys


def load(path):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def dump(doc, path):
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(doc, fh, indent=2)
        fh.write("\n")


def substitute_values(doc, mapping):
    """Recursively replace {{KEY}} placeholders in string values."""
    if isinstance(doc, dict):
        return {k: substitute_values(v, mapping) for k, v in doc.items()}
    if isinstance(doc, list):
        return [substitute_values(v, mapping) for v in doc]
    if isinstance(doc, str):
        for key, value in mapping.items():
            doc = doc.replace("{{" + key + "}}", value)
        return doc
    return doc


def cmd_substitute(template, out, pairs):
    mapping = {}
    for pair in pairs:
        key, sep, value = pair.partition("=")
        if not sep:
            raise SystemExit(f"substitute: expected KEY=VALUE, got {pair!r}")
        mapping[key] = value
    dump(substitute_values(load(template), mapping), out)


def cmd_set_channel(template, out, channel):
    policy = load(template)
    policy["notificationChannels"] = [channel]
    dump(policy, out)


def cmd_display_name(policy_file):
    print(load(policy_file)["displayName"])


def cmd_find_policy(policies_json, display_name):
    policies = load(policies_json)
    match = next(
        (p["name"] for p in policies if p.get("displayName") == display_name),
        "",
    )
    print(match)


def cmd_find_channel(channels_json, email):
    channels = load(channels_json)
    match = next(
        (
            c["name"]
            for c in channels
            if c.get("type") == "email"
            and c.get("labels", {}).get("email_address") == email
        ),
        "",
    )
    print(match)


COMMANDS = {
    "substitute": (cmd_substitute, 2),
    "set-channel": (cmd_set_channel, 3),
    "display-name": (cmd_display_name, 1),
    "find-policy": (cmd_find_policy, 2),
    "find-channel": (cmd_find_channel, 2),
}


def main(argv):
    if len(argv) < 2 or argv[1] not in COMMANDS:
        cmds = ", ".join(sorted(COMMANDS))
        raise SystemExit(f"usage: {argv[0]} <{cmds}> ...")
    fn, fixed = COMMANDS[argv[1]]
    rest = argv[2:]
    if argv[1] == "substitute":
        if len(rest) < 2:
            raise SystemExit("usage: json_render.py substitute <template> <out> KEY=VALUE ...")
        fn(rest[0], rest[1], rest[2:])
    elif len(rest) != fixed:
        raise SystemExit(f"usage: json_render.py {argv[1]} expects {fixed} args")
    else:
        fn(*rest)


if __name__ == "__main__":
    main(sys.argv)
