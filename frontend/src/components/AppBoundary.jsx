import { Component } from 'react';
export default class AppBoundary extends Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() {
    if (this.state.failed) return <main className="crash-page"><span className="arena-eyebrow">LET’S GET YOU BACK IN</span><h1>Something went off course.</h1><p>Reload to recover. Your saved editor drafts remain in this browser.</p><button className="arena-primary" onClick={() => window.location.reload()}>Reload page ↻</button><a href="#/help" onClick={() => this.setState({ failed: false })}>Get help ↗</a></main>;
    return this.props.children;
  }
}
