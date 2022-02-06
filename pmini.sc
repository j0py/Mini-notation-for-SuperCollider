/*

create a tree from a spec (a string), which can then be traversed

node types for the tree (and the list too):
- nested : contains one or more children, who share time
- turns  : contains one or more children, taking turns, one per cycle
- note   : plays a note (\degree, \dur, \type)
- rest   : plays a rest (\dur, \type)
- space  : adds sustain (time) to previous node, by increasing its \dur
- root   : is essentially a nested node, traversal start point

spec: " 1x<6[78]>4"

tree: root - space
           - note (1)
           - rest
           - turns  - note (6)
                    - nested - note (7)
                             - note (8)
           - note (4)

traverse the tree, per cycle (total dur = 1) number (number/dur):
0 : space(/.2), note(1/.2), rest(/.2), note(6/.2), note(4/.2)
1 : space(/.2), note(1/.2), rest(/.2), note(7/.1), note(8/.1), note(4/.2)
2 : space(/.2), note(1/.2), rest(/.2), note(6/.2), note(4/.2)
3 : space(/.2), note(1/.2), rest(/.2), note(7/.1), note(8/.1), note(4/.2)
etc

each cycle, you can traverse the tree, and add the nodes to the end of a list.

algoritm when you have to generate the next event:

0. while list is empty, cycle++ and traverse tree to add nodes to the list
1. take node N from the start of the list
2. if N == space then play a \rest -> done
3. do forever
     if list is empty, cycle++ and traverse tree to add nodes to the list
     if space node at start of list: take it out (M) and add it's \dur to N 
     else break from do forever
4. N == rest ? play a \rest -> done
5. N == note ? play a \note -> done
6. error

this algorithm can be implemented inside a Pn/Plazy construct
*/

Pmini {
	*new { |spec| ^super.new.init(spec); }

	init { |spec|
    if(spec.toLower != spec, { ^this.init_pbind(spec); }); // uppercase detect
    ^this.init_pattern(spec);
  }

	init_pattern { |spec|
		var root = PMRoot(spec);
		^Pn(Plazy({ root.next_event.at(2).asFloat }));
	}

	init_pbind { |spec|
		var parsing, str, struct, pb;

		// split spec and determine who does the structure
    // each uppercase char is surrounded by 2 spaces.
    // add one extra space at the end, because then you can just
    // remove the first and last space char of every part after parsing.
    spec = spec ++ " ";

		str = Dictionary.new;
		spec.asString.do { |ch|
			case
			{ ch.isAlpha.and(ch.isUpper) } { parsing = ch.toLower.asSymbol }
      { parsing.isNil } { } // wait for the first uppercase char
			{ ch == $+ } { struct = parsing }
			{ str.put(parsing, str.atFail(parsing,"") ++ ch.asString); };
			if(struct.isNil.and(parsing.notNil), { struct = parsing; });
		};

		// remove first and last char of each part and put parsers in
		str.keys.do { |it| 
      var spec = str.at(it);
      spec = spec.rotate(1).copyToEnd(2);
      (it.asString + ":" + "'" ++ spec ++ "'").postln;
      if(it == \s, { str.put(\s, spec) }, { str.put(it, PMRoot(spec)) });
    };
		
		// make a Pbind
		pb = Pbind.new;

		// this will allow different lengths of Plazy results for the parts
		[\n, \a, \p].do { |it|
			if(str.includesKey(it), {
				pb = Pbindf(
					pb,
					[$t,$d,$n].collect { |c| (it ++ c).asSymbol },
					Pn(Plazy({ str[it].next_event }))
				);	
			});
		};

		pb = Pbindf(
			pb,

			\type, Pfunc({|ev|
				case
				{ ev.atFail(\nt,"") == \rest } { \rest }
				{ ev.atFail(\at,"") == \rest } { \rest }
				{ ev.atFail(\pt,"") == \rest } { \rest }
				{ \note };
			}),

			\degree, Pfunc({|ev|
				case
				{ ev[\type] == \rest } { 0 }
				{ ev.includesKey(\nn) } { ev[\nn].asInteger }
				{ 0 };
			}),

			\instrument, Pfunc({|ev|
				case
				{ ev[\type] == \rest } { \default }
				{ str.includesKey(\s).not } { \default }
				{ Library.at(\samples, str[\s].asSymbol).notNil } { \playbuf }
				{ str[\s].asSymbol };
			}),

			\bufnum, Pfunc({|ev|
				case
				{ ev[\type] == \rest } { 0 }
				{ ev[\instrument] == \playbuf }
				{ Library.at(\samples, str[\s].asSymbol).wrapAt(ev[\degree]).bufnum }
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
				{ ev.includesKey(\an) } { ev[\an].asInteger.clip(0,8) / 8 }
				{ 1 };
			}),
			
			\pan, Pfunc({|ev|
				case
				{ ev[\type] == \rest } { 0 }
				{ ev.includesKey(\pn) } { ev[\pn].asInteger.clip(0,8) / 4 - 1 }
				{ 0 };
			}),
		);
		
		^pb;
	}
}

PMRoot : PMNested {
	var index = 0, str, cycle, events;

	*new { |spec| ^super.new.init(spec.asString); }

	init { |spec| str = spec; cycle = -1; events = List.new; ^this.parse(this) }

	parse { |currentNode|
		var node;

		while
		{ index < str.size } {
			var ch = str.at(index);
			index = index + 1;

      case
      { "0123456789".contains(ch.asString) } {
		  	node = PMNote.new;
        node.value_(ch.asString);
	  		currentNode.addChild(node);
      }
      { ch == $x } {
		  	node = PMRest.new;
	  		currentNode.addChild(node);
      }
      { ch == $  } {
		  	node = PMSpace.new;
	  		currentNode.addChild(node);
      }
			{ ch == $< } {
				node = PMTurns.new;
				this.parse(node);
				currentNode.addChild(node);
			}
			{ ch == $> } { ^this }
			{ ch == $[ } {
				node = PMNested.new;
				this.parse(node);
				currentNode.addChild(node);
			}
			{ ch == $] } { ^this }

			{ }; // default no action
		};

		^this; // you are the root node of the tree, so return yourself
	}

  traverse {
		cycle = cycle + 1;
    ^super.do_traverse(cycle, 1);
  }

  post_traverse {
    this.traverse.do { |it| it.postln };
  }

  next_event {
    var event, loop=1;
    if(events.size <= 0, { events.addAll(this.traverse) });
    event = events.removeAt(0);
    if(event.at(0) == \space, { event.put(0, \rest); ^event });

    while { loop > 0 } {
      if(events.size <= 0, { events.addAll(this.traverse) });
      if(events.at(0).at(0) == \space, {
        event.put(1, event.at(1) + events.at(0).at(1));
        events.removeAt(0);
      }, { loop = 0 });
    };

    ^event;
  }

	make_events { |default_event|
		var result;
		cycle = cycle + 1;
		result = super.get_events(cycle, 1);
		if(result.size > 0, { ^result; });

		^List.new.add(default_event);
	}

  post_events {
    this.make_events.do { |ev| ev.postln; };
  }
}

PMNote : PMNode {
	get_new_events { |cycle, dur|
		var type=\note, result = List.new;
		if(value == "x", { type = \rest; });
		result.add([dur * stretch, value, type]);
		^result;
	}

  do_traverse { |cycle, dur|
    var result = List.new;
    result.add([\note, dur, value]);
    ^result;
  }
}

PMRest : PMNode {
	get_new_events { |cycle, dur|
		var type=\note, result = List.new;
		if(value == "x", { type = \rest; });
		result.add([dur * stretch, value, type]);
		^result;
	}

  do_traverse { |cycle, dur|
    var result = List.new;
    result.add([\rest, dur, 0]);
    ^result;
  }
}

PMSpace : PMNode {
	get_new_events { |cycle, dur|
		var type=\note, result = List.new;
		if(value == "x", { type = \rest; });
		result.add([dur * stretch, value, type]);
		^result;
	}

  do_traverse { |cycle, dur|
    var result = List.new;
    result.add([\space, dur, 0]);
    ^result;
  }
}

PMNested : PMNode {
	get_new_events { |cycle, dur|
		var result, charcount=0;

		children.do({ |node| charcount = charcount + node.value.size });

		result = List.new;

		children.do({ |node|
      var d = dur / charcount * node.value.size * stretch;
			result.addAll(node.get_events(cycle, d));
		});

		^result;
	}

  do_traverse { |cycle, dur|
    var result = List.new, d = dur / children.size;

    children.do { |node| result.addAll(node.do_traverse(cycle, d)) }
    ^result;
  }
}

PMTurns : PMNode {
	get_new_events { |cycle, dur|
		var node = children.wrapAt(cycle);
		^node.get_events(cycle, dur * stretch);
	}

  do_traverse { |cycle, dur|
    var result = List.new;
		var node = children.wrapAt(cycle);
    result.addAll(node.do_traverse(cycle, dur));
    ^result;
  }
}

PMNode {
	var <>parent, <children, <>prev, <>next, <>value, <>stretch=1.0;
	var remaining_events, remain = 0;

	*new { ^super.new.initPMRoot; }

	initPMRoot {
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

	// @return List[[dur, value, type],[dur, value, type],..]
	get_events { |cycle, dur|
		var result = List.new;
		var stop = 0, duration = dur;

		while
		{ (duration > 0).and(stop <= 0) }
		{
			if(remain > 0, {
				if(duration >= remain, {
					result.add([remain, "x", 0, \rest]);
					duration = duration - remain;
					if(duration < 0.0001, { duration = 0; });
					remain = 0;
				}, {
					result.add([duration, "x", 0, \rest]);
					remain = remain - duration;
					if(remain < 0.0001, { remain = 0; });
					duration = 0;
				});
			}, {
				if(remaining_events.size > 0, {
					var event = remaining_events.removeAt(0);
					if(event[0] > duration, {
						result.add([duration, event[1], event[3]]);
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

	log { ^format("% \"%\"", this.class.name, value.asString) }
}

