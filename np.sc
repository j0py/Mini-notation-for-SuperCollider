// NodeProxy wrapper, enabling Tidal (-like) syntax
//
// example: NP(~a).snd("bd bd hh sn").play(0.8).mon(0.5)
//
NP {
	var proxy, <sound, <notes, <amps, <struct, <index;

	*new { |a_proxy, index=10| ^super.new.init(a_proxy, index); }

	init { |a_proxy, a_index|
		proxy = a_proxy;
    index = a_index;

    if(proxy.source.isNil, {
        // create a silent proxy, where things can be added on the slots
		    proxy.ar(2);
		    proxy.source_({ Silent.ar!2 });

        // you should prime the proxy with the desired synthdef
        // so that you will have controls for synthdef parameters
        // that are not set through patterns. snd() should do that.
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

		var envir = (np: this, cycle: -1); // environment for pbind

    // add Pbind to the NodeProxy
		proxy.put(
			index,
			Penvir(envir, Pbind(

        // calc cycle number (and store in envir for Plazy's)
				// generate a new dur pattern for the cycle
				// there may be alternating steps, fast/slow steps
				\dur, Pn(Plazy({ |ev| 
            ~cycle = ~cycle + 1;
            Pseq(~np.struct.durs(~cycle));
        })),

				\amp, Pn(Plazy({ |ev|
          var amps = [ amp ];

					// amps will override
					if(~np.amps.notNil, { amps = ~np.amps.names(~cycle) });

					Pseq(amps).asFloat.clip(0.0, 1.0);
				})),

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
          sample = NPSamples.samples.atFail(sound, nil);

          case
          { ev.type == \rest } { sound = \default }

          { sample.notNil }
          { 
            ev.bufnum = sample.wrapAt(note).bufnum;
            sound = \np_playbuf;
          }

          {
            if(note < 20, { note = note + 60 }); // degree
            ev.midinote = note;
          }

          sound; // the synthdef for the next event
				}),

        // debugging
				\trace, Pfunc({|ev|
					~cycle.asString 
          + ev.dur.asString 
          + ev.soundname 
          + ev.notename.asString
          + ev.bufnum.asString;
				}),

        // problem:
        // to get the synthdef controls in a gui, we must prime the
        // nodeproxy with the synthdef name (and we must alter nodeMap
        // for every event). But is you prime a nodeProxy, all slots are
        // cleared, and so this Pbind will be gone.
        // I think we can prime the nodeProxy one time only, and so the
        // nodeProxy can play samples, or just one synthdef.

        // wrapForNodeProxy.sc: nodeMap values (gui!) override event values
        // \dummy, Pfunc({ |ev| a.nodeMap.set(\pos, ev.pos); }),
        // loop them using SynthDesc.controlNames

			).trace(\trace))
		);

		^this;
	}
}
