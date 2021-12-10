// NodeProxy wrapper, enabling Tidal (-like) syntax
//
// example: NP(~a).snd("bd bd hh sn").play(0.8).mon(0.5)
//
NP {
	var proxy, <sound, <notes, <struct, params, <index;

	*new { |a_proxy, index=10| ^super.new.init(a_proxy, index); }

	init { |a_proxy, a_index|
		proxy = a_proxy;
		index = a_index;
		params = IdentityDictionary.new(0);

		SynthDef(\np_playbuf, {
			arg out=0, bufnum, amp=1, pan=0, spread=1;
			var sig;
			sig = PlayBuf.ar(2, bufnum, BufRateScale.kr(bufnum), doneAction: 2);
			sig = Splay.ar(sig, spread, amp, pan);
			Out.ar(out, sig);
		}).add;

		SynthDef(\np_silent, {
			arg out=0;
			var sig = Silent.ar!2;
			Out.ar(out, sig);
		}).add;

		if(proxy.source.isNil, {
			// create a silent proxy, where things can be added on the slots
			proxy.ar(2);
			proxy.source_({ Silent.ar!2 });
		});

		^this;
	}

	snd { |val|
		sound = NPParser.new.parse(val).post;
		if(struct.isNil, { struct = sound; });
		^this;
	}

	snd_ { |val| struct = nil; ^this.snd(val); }

	num { |val|
		notes = NPParser.new.parse(val).post;
		if(struct.isNil, { struct = notes; });
		^this;
	}

	num_ { |val| struct = nil; ^this.num(val); }

	// set values for arbitrary parameter
	param { |name, val|
		params.put(name.asSymbol, NPParser.new.parse(val));
		if(struct.isNil, { struct = params.at(name.asSymbol); });
		^this;
	}

	param_ { |name, val| struct = nil; ^this.param(name, val); }

	// plays the NodeProxy.
	// @param vol: volume for the monitor
	mon { |vol=0|
		proxy.play(vol: vol.asFloat.clip(0.0, 1.0));
		^this;
	}

	// adds a Pbind that will play on the NodeProxy private bus
	// @param amp: volume (0.0 - 1.0) for the Pbind
	play { |amp|

		var envir = (np: this, cycle: -1);

		var pb = Pbind(
			// calc cycle number (and store in envir for Plazy's)
			// generate a new dur pattern for the cycle
			// there may be alternating steps, fast/slow steps
			\dur, Pn(Plazy({ |ev|
				~cycle = ~cycle + 1;
				Pseq(~np.struct.durs(~cycle));
			})),

			\amp, amp,

			\soundname, Pn(Plazy({ |ev| Pseq(~np.sound.names(~cycle)); })),

			\notename, Pn(Plazy({ |ev|
				var notes = ~np.sound.notes(~cycle);

				if(~np.notes.notNil, { notes = ~np.notes.names(~cycle); });

				Pseq(notes); // strings!
			})),

			// the "~" name denotes a Rest to be played
			\type, Pfunc({ |ev|
				case
				{ ev.soundname == "~" } { \rest }
				{ ev.notename == "~" } { \rest }
				{ \note };
			}),

			\instrument, Pfunc({ |ev|
				var sample, sound, note;

				ev.bufnum = 0;
				ev.midinote = 0;
				note = ev.notename.asInteger;
				sound = ev.soundname.asSymbol;
				sample = Samples.samples.atFail(sound, nil);

				case
				{ ev.type == \rest } { sound = \np_silent; }

				{ sample.notNil }
				{
					ev.bufnum = sample.wrapAt(note).bufnum;
					sound = \np_playbuf;
				}

				{
					if(note < 20, { note = note + 60 }); // degree
					ev.midinote = note;
				};

				sound;
			}),
		);

		params.keysValuesDo({ |key, val|
			// maybe block keys like 'dur', 'out', 'i_out'
			pb = Pbindf(
				pb,
				key.asSymbol,
				Pn(Plazy({ |ev|
					Pseq(val.names(~cycle)).asFloat;
				}))
			);
		});

		pb = Pbindf(
			pb,
			\trace,
			Pfunc({|ev|	~cycle.asString + ev })
		).trace(\trace);

		// add Pbind to the NodeProxy
		proxy.put(index, Penvir(envir, pb));

		// wrapForNodeProxy.sc: nodeMap values (gui!) override event values

		^this;
	}
}
