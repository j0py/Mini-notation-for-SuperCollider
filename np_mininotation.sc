// Recursive mini-notation parser.
// Parses a string, reulting in a tree of objects (NPParserNode's).
// The NPParser itself is the root node, it only has children, no siblings.

// After parsing the specification string into a tree structure, we can
// "walk" through the tree structure to extract a flat list of durations,
// names, numbers, etc. The NPParser object has the methods for that.

NPParser : NPParserNode {
	var index = 0, value="", str;

  // parse input string, return a tree-structure of steps
  //
	parse { |input| str = input.asString; ^this.parseNodes(this) }

  // using the class variables "index" and "str", we go the string one time.
  // class variable "value" is used to hold parsed characters that comprise
  // a string that is being parsed.
  //
  // @param cur: the current node in the tree ("where we are")
  //
	parseNodes { |cur|
		var node;

		while
		{ index < str.size } {
			var ch = str.at(index);

			case
			{ ch == $  } { this.addNPValueNode(cur) }
			{ ch == $[ } {
				this.addNPValueNode(cur);
				this.parseNodes(node = NPNestingNode.new);
				if(node.is_valid, { cur.addChild(node) });
			}
			{ ch == $] } { ^this.addNPValueNode(cur) }
			{ ch == $< } {
				this.addNPValueNode(cur);
				this.parseNodes(node = NPAlternatingNode.new);
				if(node.is_valid, { cur.addChild(node) });
			}
			{ ch == $> } { ^this.addNPValueNode(cur) }
			{
				value = value ++ ch.asString;
				index = index + 1;
			};
		};

		this.addNPValueNode(cur);

		^this; // you are the root node of the tree, so return yourself
	}

  // add what is available in the "value" class variable to the tree
  // in a NPValueNode object, unless the content of "value" is not valid.
  //
	addNPValueNode { |cur|
		var node;
		node = NPValueNode(value);
		if(node.is_valid, { cur.addChild(node) });
		index = index + 1;
		value = "";
	}

  // post the tree structure to the post window (used for testing the code)
	test { |in|
		str = in.asString;
		this.parseNodes(this);
		super.test;
	}

  // example specification: "bd:2 hh:3 cl sn:6" should result in:
  // durs: [1/4, 1/4, 1/4, 1/4]
  // names: ["bd", "hh", "cl", "sn"]
  // numbers: [2, 3, 0, 6]
  //
  // class NPValueNode is used for this

  // support for nested lists using "[" and "]":
  // example specification: "bd:2 [hh:3 hh:2] cl sn:6" should result in:
  // durs: [1/4, 1/8, 1/8, 1/4, 1/4]
  // names: ["bd", "hh", "hh", "cl", "sn"]
  // numbers: [2, 3, 2, 0, 6]
  //
  // class NPNestingNode is used for this

  // support for alternating steps using "<" and ">":
  // example specification: "bd:2 <hh:3 hh:2> cl sn:6" should result in:
  // durs: [1/4, 1/4, 1/4, 1/4]
  // names: ["bd", "hh", "cl", "sn"]
  // numbers: [2, 3, 0, 6] for cycle 0, 2, 4, etc
  // numbers: [2, 2, 0, 6] for cycle 1, 3, 5, etc
  //
  // class NPAlternatingNode is used for this
  // supporting alternating steps made the cycle number a necessary param

  // @return a list of durations (total sum will be 1)
  // @param cycle: cycle number (0, 1, 2, 3, .. ad infinitum)
  //
	durs { |cycle|
		var result = List.new;
		super.dur(cycle, result, 1);
		^result;
	}

  // @return a list of parsed names
  // @param cycle: cycle number (0, 1, 2, 3, .. ad infinitum)
  //
	names { |cycle|
		var result = List.new;
		super.name(cycle, result);
		^result;
	}

  // @return a list of parsed numbers
  // @param cycle: cycle number (0, 1, 2, 3, .. ad infinitum)
  //
	numbers { |cycle|
		var result = List.new;
		super.number(cycle, result);
		^result;
	}
}

// abstract superclass for all nodes
//
NPParserNode {
	var <>parent, <children, <>prev, <>next;

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

  // write yourself to the post window + your children
  //
	test { |indent=""|
		(indent ++ this.log).postln;

		children.do({ |node| node.test(indent ++ "--") });
	}

  // add duration value(s) to the result
  //
	dur { |cycle, result, d|
		d = d / children.size;
		children.do({ |node| node.dur(cycle, result, d) });
	}

  // add name(s) to the result
  //
	name { |cycle, result|
		children.do({ |node| node.name(cycle, result) });
	}

  // add number(s) to the result
  //
	number { |cycle, result|
		children.do({ |node| node.number(cycle, result) });
	}

	log { ^this.class.name }
}

// a node with a certain value (format: <name>[:<number>])
NPValueNode : NPParserNode {
	var value;

	*new { |str| ^super.new.init(str) }

	init { |str| value = str.asString; ^this }

	is_valid { ^(value.size > 0) }

	log { ^this.class.name ++ "(" ++ value ++ ")" }

	dur { |cycle, result, d| result.add(d);	}

	name { |cycle, result|
		var tmp = value.split($:);

		result.add(tmp.at(0));
	}

	number { |cycle, result|
		var tmp = value.split($:);

		if(tmp.size > 1, {
			result.add(tmp.at(1));
		}, {
			result.add(0);
		});
	}
}

// multiple subnodes, durations.sum = this duration
NPNestingNode : NPParserNode {
	is_valid { ^(children.size > 0) }
}

// multiple substeps, picked one by one (roundrobin)
NPAlternatingNode : NPParserNode {
	is_valid { ^(children.size > 0) }

	dur { |cycle, result, d|
		var node = children.wrapAt(cycle);
		node.dur(cycle, result, d);
	}

	name { |cycle, result|
		var node = children.wrapAt(cycle);
		node.name(cycle, result);
	}

	number { |cycle, result|
		var node = children.wrapAt(cycle);
		node.number(cycle, result);
	}
}
