// NodeProxy wrapper, enabling Tidal (-like) syntax
//
// example: NP(~a).snd("bd bd hh sn").play(0.8).mon(0.5)
//
NP {
	var proxy, sound, notes, amps, struct;

	*new { |a_proxy| ^super.new.init(a_proxy); }

	init { |a_proxy|
		proxy = a_proxy;
    
    // create a silent proxy, where (noisy) things can be added on the slots
		proxy.ar(2);
		proxy.source_({ Silent.ar!2 });

		^this;
	}

	snd { |val| 
    sound = NPParser.new.parse(val).post; 
    if(struct.isNil, { struct = sound; });
    ^this;
  }

	snd_ { |val| struct = nil; ^this.snd(val); }

	num { |val| 
    notes = NPParser.new.parse(val); 
    if(struct.isNil, { struct = notes; });
    ^this;
  }

	num_ { |val| struct = nil; ^this.num(val); }

	amp { |val| 
    amps = NPParser.new.parse(val); 
    if(struct.isNil, { struct = amps; });
    ^this;
  }

	amp_ { |val| struct = nil; ^this.amp(val); }

	// plays the NodeProxy.
  // @param vol: volume for the monitor
	mon { |vol=0|
		proxy.play(vol: vol.asFloat.clip(0.0, 1.0));
		^this;
	}

  // adds a Pbind that will play on the NodeProxy private bus
  // @param amp: volume (0.0 - 1.0) for the Pbind
	play { |amp|

    // use an environment to count the cycles and hold all the data
		var envir = (
			sound: sound,
			notes: notes,
      amps: amps,
      struct: struct,
			cycle: -1,
		);

    // add Pbind to the NodeProxy
		proxy.put(
			10, // should be parameter?
			Penvir(envir, Pbind(

        // Following Plazy constructs depend on the ~cycle value
        \cycle, Pn(Plazy({ |ev| ~cycle = ~cycle + 1; })),

				// generate a new dur pattern per cycle
				// there may be alternating steps, fast/slow steps
				\dur, Pn(Plazy({ |ev| Pseq(~struct.durs(~cycle)); })),

				\amp, Pn(Plazy({ |ev|
          var amps = [ amp ];

					// amps will overrule
					if(~amps.notNil, { amps = ~amps.names(~cycle) });

					Pseq(amps).asFloat.clip(0.0, 1.0);
				})),

				\samplename, Pn(Plazy({ |ev| Pseq(~sound.names(~cycle)); })),

				\samplenumber, Pn(Plazy({ |ev|
					var notes = ~sound.notes(~cycle);

					if(~notes.notNil, { notes = ~notes.names(~cycle); });

					Pseq(notes).asInteger;
				})),

        // the "~" name denotes a Rest to be played
				\type, Pfunc({ |ev|
					if(ev.samplename == "~", \rest, \note);
				}),

				\instrument, Pfunc({ |ev|
          if(ev.samplename == "~", \default, {
            var sample = NPSamples.samples
					  .at(ev.samplename.asSymbol);

					  if(sample.notNil,
						  \np_playbuf,
						  ev.samplename.asSymbol
					  );
          });
				}),

				\midinote, Pfunc({ |ev|
					if(ev.samplenumber < 20, {
						ev.samplenumber + 60;
					}, ev.samplenumber);
				}),

				\bufnum, Pfunc({ |ev|
          if(ev.samplename == "~", 0, {
					  var sample = NPSamples.samples
					  .at(ev.samplename.asSymbol);

					  if(sample.notNil, {
						  sample.wrapAt(ev.samplenumber).bufnum;
					  }, 0);
          });
				}),

        // debugging
				\trace, Pfunc({|ev|
					~cycle.asString 
          + ev.dur.asString 
          + ev.samplename 
          + ev.samplenumber.asString
          + ev.bufnum.asString;
				}),
			).trace(\trace))
		);

		^this;
	}
}
