// Recursive mini-notation parser.
// Parses a string, resulting in a tree of objects (NPParserNode's).
// The NPParser itself is the root node, it only has children, no siblings.

// After parsing the specification string into a tree structure, we can
// "walk" through the tree structure to extract a flat list of durations,
// names, notes, etc. The NPParser object has the methods for that.

NPParser : NPNestingNode {
	var index = 0, str, last_cycle, events;

	parse { |input| str = input.asString ++ " "; ^this.parseNodes(this) }

	parseNodes { |currentNode|
		var node, name="", note="", fast="", slow="", mul="", parsing="name";

		while
		{ index < str.size } {
			var ch = str.at(index);
			index = index + 1;

			if(" []<>".contains(ch.asString), {
				if(name.size > 0, { node.name_(name.asString); });
				if(note.size > 0, { node.note_(note.asString); });
				if(fast.size > 0, { node.muldur(fast.asFloat.reciprocal); });
				if(slow.size > 0, { node.muldur(slow.asFloat); });
				if(mul.size > 0, { node.muldur(mul.asFloat); });

				node = nil;
				name = "";
				note = "";
				fast = "";
				slow = "";
				mul = "";
				parsing = "name";
			});

			case
			{ ch == $[  } {
				node = NPNestingNode.new;
				this.parseNodes(node);
				currentNode.addChild(node);
			}
			{ ch == $]  } { ^this }

			{ ch == $< } {
				node = NPAlternatingNode.new;
				this.parseNodes(node);
				currentNode.addChild(node);
			}
			{ ch == $> } { ^this }

			{ ch == $: } { parsing = "note"; }
			{ ch == $* } { parsing = "fast"; }
			{ ch == $/ } { parsing = "slow"; }
			{ ch == $@ } { parsing = "mul"; }

			{
				if(parsing == "name", {
					if(ch != $ , {
						name = name ++ ch.asString;

						if(node.isNil, {
							node = NPValueNode.new;
							currentNode.addChild(node);
						}, {
							if(node.is_valuenode.not, {
								node = NPValueNode.new;
								currentNode.addChild(node);
							});
						});
					});
				});

				if(parsing == "note", { note = note ++ ch.asString; });
				if(parsing == "fast", { fast = fast ++ ch.asString; });
				if(parsing == "slow", { slow = slow ++ ch.asString; });
				if(parsing == "mul", { mul = mul ++ ch.asString; });
			};
		};

		^this; // you are the root node of the tree, so return yourself
	}

	make_events { |cycle|
		last_cycle = last_cycle ? -1;
		if(last_cycle < cycle, {
			last_cycle = cycle;
			events = super.get_events(cycle, 1);
		});
		^events;
	}

	durs { |cycle| ^this.make_events(cycle).collect({ |ev| ev[0] }) }

	names { |cycle| ^this.make_events(cycle).collect({ |ev| ev[1] }) }

	notes { |cycle| ^this.make_events(cycle).collect({ |ev| ev[2] }) }
}

// abstract superclass for all nodes
NPParserNode {
	var <>parent, <children, <>prev, <>next, <>name, <>note, <>stretch=1.0;
	var remaining_events, remain = 0;

	*new { ^super.new.initNPParserNode; }

	initNPParserNode {
    children = List.new;
    remaining_events = List.new;
    ^this
  }

	addChild { |node|
		node.parent_(this);
		if(children.size > 0, {
			children.last.next_(node);
			node.prev_(children.last);
		});

		children.add(node);
	}

	muldur { |factor| stretch = stretch * factor; ^this; }

	// @return List[[dur, name, note],[dur, name, note],..]
	get_events { |cycle, dur|
		var result = List.new;
		var duration = dur;

		format("get_events(%, %)", cycle, dur).postln;

		while
		{ duration > 0 }
		{
			format("% %", duration, remaining_events.size).postln;

			if(remain > 0, {
				if(duration >= remain, {
					result.add([remain, "~", "~"]);
					duration = duration - remain;
					if(duration < 0.0001, { duration = 0; });
					remain = 0;
				}, {
					result.add([duration, "~", "~"]);
					remain = remain - duration;
					if(remain < 0.0001, { remain = 0; });
					duration = 0;
				});
			}, {
				if(remaining_events.size > 0, {
					var event = remaining_events.removeAt(0);
					if(event[0] > duration, {
						result.add([duration, event[1], event[2]]);
						remain = event[0] - duration;
						if(remain < 0.0001, { remain = 0; });
						duration = 0;
					}, {
						result.add(event);
						duration = duration - event[0];
						if(duration < 0.0001, { duration = 0; });
					});
				}, {
					format("  get_events(%, %)", cycle, dur).postln;
					remaining_events.addAll(this.get_new_events(cycle, dur));
				});
			});
		};

		^result;
	}

	post { |indent=""|
		(indent ++ this.log).postln;

		children.do({ |node| node.post(indent ++ "--") });

		^this;
	}

	log {
		^format(
			"% % % %",
			this.class.name,
			name.asString,
			(note ? 0).asString,
			stretch.asFloat
		);
	}

	is_valuenode { ^false; }
}

// support for a value step (format: <name>[:<note>][*<fast>][/<slow>])
// example specification: "bd:2 hh:3 cl sn:6" should result in:
// durs: [1/4, 1/4, 1/4, 1/4]
// names: ["bd", "hh", "cl", "sn"]
// notes: [2, 3, 0, 6]
NPValueNode : NPParserNode {
	get_new_events { |cycle, dur|
		var result = List.new;
		result.add([dur * stretch, name, note ? 0]);
		^result;
	}

	is_valuenode { ^true; }
}

// support for nested lists using "[" and "]":
// example specification: "bd:2 [hh:3 hh:2] cl sn:6" should result in:
// durs: [1/4, 1/8, 1/8, 1/4, 1/4]
// names: ["bd", "hh", "hh", "cl", "sn"]
// notes: [2, 3, 2, 0, 6]
NPNestingNode : NPParserNode {
	get_new_events { |cycle, dur|
		var result, d = dur / children.size * stretch;

		result = List.new;
		children.do({ |node|
			// we have to use same cycle number..
			result.addAll(node.get_events(cycle, d));
		});
		^result;
	}
}

// support for alternating steps using "<" and ">" (round-robin)
// example specification: "bd:2 <hh:3 hh:2> cl sn:6" should result in:
// durs: [1/4, 1/4, 1/4, 1/4]
// names: ["bd", "hh", "cl", "sn"]
// numbers: [2, 3, 0, 6] for cycle 0, 2, 4, etc
// numbers: [2, 2, 0, 6] for cycle 1, 3, 5, etc
NPAlternatingNode : NPParserNode {
	get_new_events { |cycle, dur|
		var node = children.wrapAt(cycle);
		^node.get_events(cycle, dur * stretch);
	}
}
