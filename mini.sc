/*
Recursive tidal mini-notation parser.
Parses a string, resulting in a tree of objects (TParserNode's).
Mbind itself is the root node, it only has children, no siblings.
Mbind is meant to be used inside a Pbind.

returns Pn(Plazy({ .. Pseq(List([<dur>, <sound>, <note>, <type>], [<dur>, <sound>, <note>, <type>], ..))}))

<spec> is a string in mini-notation format, like "bd:4 hh:2 [cl:1 cl:2] sn"
<dur> is a pattern for the duration with float values
<type> is a pattern containing \note or \rest symbols
<sound> is a pattern containing symbols from the <spec>
<note> is a pattern containing integer numbers from the <spec>

- you can combine <sound> and <note> to determine a sample buffer number
  expects samples in Library.at(\samples, ...)
- <sound> can also specify an instrument, and <note> a midinote number to play
- <sound> can also specify midinote numbers (convert the string to integer)
  you would then use another pattern to specifiy the \instrument key

Mbind and Mnum make life even more fun:

~a[0] = Pbindf(
	Mbind("S sinperc N+ 1 2 3 4 A 2 P -6 6"),
	\amp, Mnum("0.25 <0.5 1> 0.75"),
	\pan, Pwhite(-1, 1),
	\atk, Mnum("0.05 <0.2 0.5> 0.02"),
);
*/

Mbind {
	*new { |input| ^super.new.init(input); }

	// @return a Pbind
	init { |spec|
		var parsing, str, struct, pb;

		// split spec and determine who does the structure
		str = Dictionary.new;
		spec.asString.do { |ch|
			case
			{ ch.isAlpha.and(ch.isUpper) } { parsing = ch.toLower.asSymbol }
			{ ch == $+ } { struct = parsing }
			{ str.put(parsing, str.atFail(parsing,"") ++ ch.asString); };
			if(struct.isNil.and(parsing.notNil), { struct = parsing; });
		};

		// put parsers in
		str.keys.do { |it| str.put(it, Mini(str.at(it))) };
		
		// play something
		pb = Pbind.new;

		// this will allow different lengths of Plazy results for the 4 parts
		[\s, \n, \a, \p].do { |it|
			if(str.includesKey(it), {
				pb = Pbindf(
					pb,
					[$d,$s,$n,$t].collect { |c| (it ++ c).asSymbol },
					Pn(Plazy({ Pseq(str[it].make_events(), 1) }))
				);	
			});
		};

		pb = Pbindf(
			pb,

			\type, Pfunc({|ev|
				case
				{ ev.atFail(\ss,"").asString == "~" } { \rest }
				{ ev.atFail(\ns,"").asString == "~" } { \rest }
				{ ev.atFail(\as,"").asString == "~" } { \rest }
				{ ev.atFail(\ps,"").asString == "~" } { \rest }
				{ \note };
			}),

			\degree, Pfunc({|ev|
				case
				{ ev[\type] == \rest } { 0 }
				{ ev.includesKey(\ns) } { ev[\ns].asInteger }
				{ ev.includesKey(\sn) } { ev[\sn].asInteger }
				{ 0 };
			}),

			\instrument, Pfunc({|ev|
				case
				{ ev[\type] == \rest } { \default }
				{ ev.includesKey(\ss).not } { \default }
				{ Library.at(\samples, ev[\ss].asSymbol).notNil } { \playbuf }
				{ ev[\ss].asSymbol };
			}),

			\bufnum, Pfunc({|ev|
				case
				{ ev[\type] == \rest } { 0 }
				{ ev[\instrument] == \playbuf }
				{ Library.at(\samples, ev[\ss].asSymbol).wrapAt(ev[\degree]).bufnum }
				{ 0 };
			}),

			\dur, Pfunc({|ev|
				var key = (struct ++ $d).asSymbol;
				case
				{ ev.includesKey(key) } { ev[key].asFloat }
				{ 1 };
			}),
			
			\amp, Pfunc({|ev|
				case
				{ ev[\type] == \rest } { 0 }
				{ ev.includesKey(\as) } { ev[\as].asInteger.clip(0,9) / 9 }
				{ 1 };
			}),
			
			\pan, Pfunc({|ev|
				case
				{ ev[\type] == \rest } { 0 }
				{ ev.includesKey(\ps) } { ev[\ps].asInteger.clip(-9,9) / 9 }
				{ 0 };
			}),
		);
		
		^pb;
	}
}

Mnum {
	var parser;
	
	*new { |input| ^super.new.init(input); }

	// @return a Pattern
	init { |input|
		parser = Mini(input);

		^Pn(Plazy({
			Pseq(parser.make_events().flop.at(1).asFloat, 1);
		}));
	}
}

Mini : NestingMiniNode {
	var index = 0, str, cycle;

	*new { |input| ^super.new.init(input.asString ++ " "); }

	init { |input| str = input;	cycle = -1;	^this.parseNodes(this);	}

	make_events { |default_event|
		var result;
		cycle = cycle + 1;
		result = super.get_events(cycle, 1);
		if(result.size > 0, { ^result; });

		^List.new.add(default_event);
	}

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
				node = NestingMiniNode.new;
				this.parseNodes(node);
				currentNode.addChild(node);
			}
			{ ch == $]  } { ^this }

			{ ch == $< } {
				node = AlternatingMiniNode.new;
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
							node = ValueMiniNode.new;
							currentNode.addChild(node);
						}, {
							if(node.is_valuenode.not, {
								node = ValueMiniNode.new;
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
}

// support for a value step (format: <name>[:<note>][*<fast>][/<slow>])
// example specification: "bd:2 hh:3 cl sn:6" should result in:
// durs: [1/4, 1/4, 1/4, 1/4]
// names: ["bd", "hh", "cl", "sn"]
// notes: [2, 3, 0, 6]
ValueMiniNode : MiniNode {
	get_new_events { |cycle, dur|
		var type=\note, result = List.new;
		if(name == "~", { type = \rest; });
		if(note == "~", { type = \rest; });
		result.add([dur * stretch, name, (note ? 0).asInteger, type]);
		^result;
	}

	is_valuenode { ^true; }
}

// support for nested lists using "[" and "]":
// example specification: "bd:2 [hh:3 hh:2] cl sn:6" should result in:
// durs: [1/4, 1/8, 1/8, 1/4, 1/4]
// names: ["bd", "hh", "hh", "cl", "sn"]
// notes: [2, 3, 2, 0, 6]
NestingMiniNode : MiniNode {
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
AlternatingMiniNode : MiniNode {
	get_new_events { |cycle, dur|
		var node = children.wrapAt(cycle);
		^node.get_events(cycle, dur * stretch);
	}
}

MiniNode {
	var <>parent, <children, <>prev, <>next, <>name, <>note, <>stretch=1.0;
	var remaining_events, remain = 0;

	*new { ^super.new.initMiniNode; }

	initMiniNode {
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

	// @return List[[dur, sound, note, type],[dur, sound, note, type],..]
	get_events { |cycle, dur|
		var result = List.new;
		var stop = 0, duration = dur;

		while
		{ (duration > 0).and(stop <= 0) }
		{
			if(remain > 0, {
				if(duration >= remain, {
					result.add([remain, "~", 0, \rest]);
					duration = duration - remain;
					if(duration < 0.0001, { duration = 0; });
					remain = 0;
				}, {
					result.add([duration, "~", 0, \rest]);
					remain = remain - duration;
					if(remain < 0.0001, { remain = 0; });
					duration = 0;
				});
			}, {
				if(remaining_events.size > 0, {
					var event = remaining_events.removeAt(0);
					if(event[0] > duration, {
						result.add([duration, event[1], event[2].asInteger, event[3]]);
						remain = event[0] - duration;
						if(remain < 0.0001, { remain = 0; });
						duration = 0;
					}, {
						result.add(event);
						duration = duration - event[0];
						if(duration < 0.0001, { duration = 0; });
					});
				}, {
					remaining_events.addAll(this.get_new_events(cycle, dur));
					if(remaining_events.size <= 0, { stop = 1; }); // prevent endless loop
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
