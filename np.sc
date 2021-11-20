// recursive mini-notation parser
// the parser is the root node, it only has children, no siblings

LogicalNode {
	var <>parent, <children, <>prev, <>next;

	*new { ^super.new.initLogicalNode; }

	initLogicalNode { children = List.new; ^this }

	addChild { |step|
		step.parent_(this);
		if(children.size > 0, {
			children.last.next_(step);
			step.prev_(children.last);
		});

		children.add(step);
	}

	test { |indent=""|
		(indent ++ this.log).postln;

		children.do({ |step| step.test(indent ++ "--") });
	}

	dur { |cycle, result, d|
		d = d / children.size;
		children.do({ |step|
			step.dur(cycle, result, d);
		});
	}

	name { |cycle, result|
		children.do({ |step| step.name(cycle, result) });
	}

	number { |cycle, result|
		children.do({ |step| step.number(cycle, result) });
	}

	log { ^this.class.name }
}

// parse str resulting in a tree-structure of steps
StepParser : LogicalNode {
	var index = 0, value="", str;

	parse { |in| str = in.asString; ^this.parseStep(this) }

	test { |in|
		str = in.asString;
		this.parseStep(this);
		super.test;
	}

	durs { |cycle|
		var result = List.new;
		super.dur(cycle, result, 1);
		^result;
	}

	names { |cycle|
		var result = List.new;
		super.name(cycle, result);
		^result;
	}

	numbers { |cycle|
		var result = List.new;
		super.number(cycle, result);
		^result;
	}

	parseStep { |cur|
		var step;

		while
		{ index < str.size } {
			var ch = str.at(index);

			case
			{ ch == $  } { this.addValueStep(cur) }
			{ ch == $[ } {
				this.addValueStep(cur);
				this.parseStep(step = MultiStep.new);
				if(step.is_valid, { cur.addChild(step) });
			}
			{ ch == $] } { ^this.addValueStep(cur) }
			{ ch == $< } {
				this.addValueStep(cur);
				this.parseStep(step = OneOfMultiStep.new);
				if(step.is_valid, { cur.addChild(step) });
			}
			{ ch == $> } { ^this.addValueStep(cur) }
			{
				value = value ++ ch.asString;
				index = index + 1;
			};
		};

		this.addValueStep(cur);

		^this;
	}

	addValueStep { |cur|
		var step;
		step = ValueStep(value);
		if(step.is_valid, { cur.addChild(step) });
		index = index + 1;
		value = "";
	}
}

// a step with a certain value (format: <sound>[:<num>])
ValueStep : LogicalNode {
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

// multiple substeps, durations.sum = this duration
MultiStep : LogicalNode {
	is_valid { ^(children.size > 0) }
}

// multiple substeps, picked one by one (roundrobin)
OneOfMultiStep : LogicalNode {
	is_valid { ^(children.size > 0) }

	dur { |cycle, result, d|
		var step = children.wrapAt(cycle);
		step.dur(cycle, result, d);
	}

	name { |cycle, result|
		var step = children.wrapAt(cycle);
		step.name(cycle, result);
	}

	number { |cycle, result|
		var step = children.wrapAt(cycle);
		step.number(cycle, result);
	}
}

// load samples, creates groups, defines a synthdef
Samples {
	classvar <groups, <samples=nil;

	*load {
		var s = Server.default;

		if(samples.notNil, { ^this; });

		s.waitForBoot({

			samples = Dictionary.new;

			("samples".resolveRelative +/+ "*")
			.pathMatch.do({|sub|
				samples.put(
					sub.basename
					.withoutTrailingSlash.asSymbol,
					(sub +/+ "*.wav")
					.pathMatch.collect({|wav|
						Buffer.read(s, wav)
					})
				);
			});

			s.sync;

			groups = Dictionary.newFrom([
				\mod, Group.tail,
				\src, Group.tail,
				\fx1, Group.tail,
				\fx2, Group.tail,
			]);

			SynthDef(\np_playbuf, {
				arg buf, amp=1, pan=0, spread=1;
				var sig;
				sig = PlayBuf.ar(2, buf, doneAction: 2);
				sig = Splay.ar(sig, spread, amp, pan);
				Out.ar(\out.kr(0), sig);
			}).add;

			s.sync;

			"Samples ready".postln;
		});
	}
}

// NP(~a).sound("bd bd hh sn").play(0.8).mon(0.5)
NP {
	var proxy, sound, notes, durmul=1;

	*new { |a_proxy| ^super.new.init(a_proxy); }

	init { |a_proxy|
		proxy = a_proxy;
		proxy.ar(2);
		proxy.source_({ Silent.ar!2 });

		^this;
	}

	snd { |val| sound = StepParser.new.parse(val); ^this }

	num { |val| notes = StepParser.new.parse(val); ^this }

	beats { |val| durmul = val.asFloat; ^this }

	// monitor at what volume?
	mon { |vol=0|
		proxy.play(vol: vol.asFloat.clip(0.0, 1.0));
		^this;
	}

	play { |amp|

		var envir = (
			sound: sound,
			notes: notes,
			durmul: durmul,
			cycle: -1,
		);

		proxy.put(
			10,
			Penvir(envir, Pbind(
				\amp, amp.asFloat.clip(0.0, 1.0),
				\group, Samples.groups[\src],

				// generate a new dur pattern per cycle
				// (because of OneOfMultiStep)
				\dur, Pn(Plazy({ |ev|
					~cycle = ~cycle + 1;
					Pseq(~sound.durs(~cycle) * ~durmul, 1);
				})),

				\samplename, Pn(Plazy({ |ev|
					Pseq(~sound.names(~cycle));
				})),

				\samplenumber, Pn(Plazy({ |ev|
					var numbers = ~sound.numbers(~cycle);

					// notes will overrule numbers
					if(notes.notNil, {
						numbers = ~notes.names(~cycle);
					});

					Pseq(numbers).asInteger;
				})),

				\type, Pfunc({ |ev|
					if(ev.samplename == "~", \rest, \note);
				}),

				\instrument, Pfunc({ |ev|
					var sample = Samples.samples
					.at(ev.samplename.asSymbol);

					if(sample.notNil,
						\np_playbuf,
						ev.samplename.asSymbol
					);
				}),

				\midinote, Pfunc({ |ev|
					if(ev.samplenumber < 20, {
						ev.samplenumber + 60;
					}, ev.samplenumber);
				}),

				\buf, Pfunc({ |ev|
					var sample = Samples.samples
					.at(ev.samplename.asSymbol);

					if(sample.notNil, {
						sample.wrapAt(ev.samplenumber);
					}, 0);
				}),

				\trace, Pfunc({|ev|
					~cycle.asString + ev.dur.asString + ev.samplename + ev.samplenumber.asString;
				}),
			).trace(\trace))
		);

		^this;
	}
}
