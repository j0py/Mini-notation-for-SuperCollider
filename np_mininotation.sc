// Recursive mini-notation parser.
// Parses a string, resulting in a tree of objects (NPParserNode's).
// The NPParser itself is the root node, it only has children, no siblings.

// After parsing the specification string into a tree structure, we can
// "walk" through the tree structure to extract a flat list of durations,
// names, notes, etc. The NPParser object has the methods for that.

NPParser : NPParserNode {
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
	var <>parent, <children, <>prev, <>next, <>name, <>note, <>durmul=1.0;

	*new { ^super.new.initNPParserNode; }

	initNPParserNode { children = List.new; ^this }

	addChild { |node|
		node.parent_(this);
		if(children.size > 0, {
			children.last.next_(node);
			node.prev_(children.last);
		});

		children.add(node);
	}

  muldur { |factor| durmul = durmul * factor; ^this; }

  // @return List[[dur, name, note],[dur, name, note],..]
  get_events { |cycle, dur|
    var d, events = List.new;
    d = dur / children.size;
    children.do({ |node| events.addAll(node.get_events(cycle, d)); });

    // TODO: put events durmul times inside dur beats 

    ^events;
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
      durmul.asFloat
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
  get_events { |cycle, dur| ^[[dur, name, note ? 0]] }

  is_valuenode { ^true; }
}

// support for nested lists using "[" and "]":
// example specification: "bd:2 [hh:3 hh:2] cl sn:6" should result in:
// durs: [1/4, 1/8, 1/8, 1/4, 1/4]
// names: ["bd", "hh", "hh", "cl", "sn"]
// notes: [2, 3, 2, 0, 6]
NPNestingNode : NPParserNode {
}

// support for alternating steps using "<" and ">" (round-robin)
// example specification: "bd:2 <hh:3 hh:2> cl sn:6" should result in:
// durs: [1/4, 1/4, 1/4, 1/4]
// names: ["bd", "hh", "cl", "sn"]
// numbers: [2, 3, 0, 6] for cycle 0, 2, 4, etc
// numbers: [2, 2, 0, 6] for cycle 1, 3, 5, etc
NPAlternatingNode : NPParserNode {
	get_events { |cycle, dur|
		var node = children.wrapAt(cycle);
		^node.get_events(cycle, dur);
	}
}
