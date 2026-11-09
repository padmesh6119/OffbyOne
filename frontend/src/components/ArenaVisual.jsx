import Icon from './Icon';

// An illustrative match preview; no simulated live scores or players.
export default function ArenaVisual() {
  return <div className="match-art" aria-hidden="true">
    <div className="art-orbit orbit-one"><span/></div><div className="art-orbit orbit-two"><span/></div>
    <div className="art-cross cross-one">+</div><div className="art-cross cross-two">+</div>
    <div className="query-art-card query-art-you">
      <div className="query-art-header"><span className="art-avatar"><Icon name="code"/></span><div><strong>You</strong></div><span className="art-tag">01</span></div>
      <div className="art-code"><span>01</span><code><b>SELECT</b> your_best</code><span>02</span><code><b>FROM</b> every_attempt</code><span>03</span><code><b>WHERE</b> focus = <i>true</i>;</code></div>
    </div>
    <div className="art-versus">vs<span/></div>
    <div className="query-art-card query-art-rival">
      <div className="query-art-header"><span className="art-avatar"><Icon name="arena"/></span><div><strong>Your rival</strong></div><span className="art-tag">02</span></div>
      <div className="art-code"><span>01</span><code><b>SELECT</b> a_better_way</code><span>02</span><code><b>FROM</b> the_same_data</code><span>03</span><code><b>ORDER BY</b> instinct;</code></div>
    </div>
  </div>;
}
